#!/usr/bin/env python3
from __future__ import annotations

import concurrent.futures as cf
import datetime as dt
import json
import os
import re
import signal
import sys
import threading
import time
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple
import subprocess

from rich.console import Console, Group
from rich.live import Live
from rich.layout import Layout
from rich.panel import Panel
from rich.table import Table
from rich.text import Text


# -----------------------------
# Config
# -----------------------------
LOG_LEVEL = os.environ.get("LOG_LEVEL", "TRACE")

SCRIPT_DIR = Path(__file__).resolve().parent
RUNNER = (SCRIPT_DIR / "run-planner.sh").resolve()
LLAMA_DIR = (SCRIPT_DIR / "config-files/v1.7/a-star/llama-8b").resolve()
CHATGPT_DIR = (SCRIPT_DIR / "config-files/v1.7/a-star/chatgpt-5-mini").resolve()
LOGS_ROOT = (SCRIPT_DIR / "planner-logs").resolve()
SUMMARY_DIR = (SCRIPT_DIR / "batch_execute").resolve()

LLAMA_PARALLEL = 1
CHATGPT_PARALLEL = 4

SUMMARY_NAME = "summary.json"

# live log display
TAIL_LINES_PER_JOB = 8
MAX_ACTIVE_PANELS = 3
MAX_FINISHED_PANELS = 3
UI_REFRESH_HZ = 6.0

# -----------------------------
# Retry policy
# -----------------------------
RETRY_ON_TIMEOUT = True
MAX_RETRIES_PER_CONFIG = 3  # retries after the first attempt
RETRY_BACKOFF_SEC = 5.0
RETRY_BACKOFF_MULT = 2.0
RETRY_BACKOFF_JITTER_SEC = 0.25

TIMEOUT_PATTERNS = [
    r"java\.net\.http\.HttpTimeoutException",
    r"request timed out",
    r"Failed after retries",
    r"Read timed out",
    r"connect timed out",
]

if not RUNNER.exists():
    raise FileNotFoundError(f"Runner not found: {RUNNER}")
if not os.access(RUNNER, os.X_OK):
    raise PermissionError(f"Runner not executable: {RUNNER} (try: chmod +x {RUNNER})")


# -----------------------------
# Utilities
# -----------------------------
def iso_now() -> str:
    return dt.datetime.now().astimezone().isoformat(timespec="seconds")


def ts_stamp() -> str:
    return dt.datetime.now().strftime("%Y%m%d-%H%M%S")


def strip_jsonc_comments(text: str) -> str:
    return re.sub(r"//.*$", "", text, flags=re.MULTILINE)


def get_max_len(cfg: Path) -> Optional[int]:
    try:
        raw = strip_jsonc_comments(cfg.read_text(encoding="utf-8"))
        obj = json.loads(raw)
        val = obj["search"]["plan"]["max-length"]
        if val is None:
            return None
        return int(val)
    except Exception as e:
        print(f"WARN: Could not read max-length for {cfg}: {e}", file=sys.stderr)
        return None


def sorted_configs(dirpath: Path) -> List[Path]:
    items: List[Tuple[int, Path]] = []
    for cfg in dirpath.glob("*.jsonc"):
        ml = get_max_len(cfg)
        if ml is None:
            continue
        items.append((ml, cfg))
    items.sort(key=lambda t: (t[0], t[1].name))
    return [p for _, p in items]


def read_text_best_effort(path: Path, max_bytes: int = 250_000) -> str:
    try:
        if not path.exists():
            return ""
        data = path.read_bytes()
        if len(data) > max_bytes:
            data = data[-max_bytes:]
        return data.decode("utf-8", errors="replace")
    except Exception:
        return ""


def tail_lines(path: Path, n: int) -> List[str]:
    txt = read_text_best_effort(path, max_bytes=200_000)
    if not txt:
        return []
    lines = txt.splitlines()
    if len(lines) <= n:
        return lines
    return lines[-n:]


_TIMEOUT_REGEXES = [re.compile(p, re.IGNORECASE) for p in TIMEOUT_PATTERNS]


def is_timeout_failure(log_path: Path) -> bool:
    txt = read_text_best_effort(log_path)
    if not txt:
        return False
    return any(rx.search(txt) for rx in _TIMEOUT_REGEXES)


def backoff_sleep(attempt_idx: int) -> None:
    base = RETRY_BACKOFF_SEC * (RETRY_BACKOFF_MULT ** (attempt_idx - 1))
    jitter = (time.time() % 1.0) * RETRY_BACKOFF_JITTER_SEC
    time.sleep(base + jitter)


def sanitize_name(s: str) -> str:
    return re.sub(r"[^A-Za-z0-9._=-]+", "_", s)


def short_cfg_name(cfg: str | Path) -> str:
    return Path(cfg).name


def duration_str(seconds: Optional[float]) -> str:
    if seconds is None:
        return "-"
    seconds = int(seconds)
    h, rem = divmod(seconds, 3600)
    m, s = divmod(rem, 60)
    if h > 0:
        return f"{h:02d}:{m:02d}:{s:02d}"
    return f"{m:02d}:{s:02d}"


def ellipsize_middle(s: str, max_len: int) -> str:
    if len(s) <= max_len:
        return s
    if max_len <= 3:
        return s[:max_len]
    left = (max_len - 3) // 2
    right = max_len - 3 - left
    return s[:left] + "..." + s[-right:]


# -----------------------------
# Rich UI state
# -----------------------------
console = Console()

_ui_lock = threading.Lock()
_ui_state: Dict[str, Any] = {
    "start_time": time.time(),
    "global_status": "running",
    "stop_reason": None,
    "run_id": None,
    "log_dir": None,
    "pools": {
        "llama-8b": {"total": 0, "running": 0, "done": 0, "ok": 0, "failed": 0},
        "chatgpt-5-mini": {"total": 0, "running": 0, "done": 0, "ok": 0, "failed": 0},
    },
    "jobs": {},  # job_id -> dict
}


def ui_init(run_id: str, log_dir: Path, pool_totals: Dict[str, int]) -> None:
    with _ui_lock:
        _ui_state["run_id"] = run_id
        _ui_state["log_dir"] = str(log_dir)
        for pool, total in pool_totals.items():
            if pool not in _ui_state["pools"]:
                _ui_state["pools"][pool] = {"total": 0, "running": 0, "done": 0, "ok": 0, "failed": 0}
            _ui_state["pools"][pool]["total"] = total


def ui_job_created(job_id: str, pool: str, cfg: Path) -> None:
    with _ui_lock:
        _ui_state["jobs"][job_id] = {
            "job_id": job_id,
            "pool": pool,
            "cfg": str(cfg),
            "status": "queued",
            "attempt": 0,
            "max_attempts": 0,
            "log_path": None,
            "exit_code": None,
            "last_update": time.time(),
            "started_at": None,
            "ended_at": None,
            "note": None,
        }


def ui_job_attempt_start(job_id: str, attempt: int, max_attempts: int, log_path: Path) -> None:
    with _ui_lock:
        job = _ui_state["jobs"].setdefault(job_id, {})
        pool = job.get("pool")
        if pool in _ui_state["pools"]:
            if job.get("status") not in {"running"}:
                _ui_state["pools"][pool]["running"] += 1

        job["status"] = "running"
        job["attempt"] = attempt
        job["max_attempts"] = max_attempts
        job["log_path"] = str(log_path)
        job["exit_code"] = None
        job["started_at"] = job.get("started_at") or time.time()
        job["ended_at"] = None
        job["note"] = None
        job["last_update"] = time.time()


def ui_job_retry_wait(job_id: str, note: str) -> None:
    with _ui_lock:
        job = _ui_state["jobs"].get(job_id)
        if not job:
            return
        job["status"] = "retry_wait"
        job["note"] = note
        job["last_update"] = time.time()


def ui_job_finished(job_id: str, ok: bool, exit_code: int, note: str) -> None:
    with _ui_lock:
        job = _ui_state["jobs"].get(job_id)
        if not job:
            return

        pool = job.get("pool")
        prev_status = job.get("status")

        if pool in _ui_state["pools"]:
            if prev_status in {"running", "retry_wait"} and _ui_state["pools"][pool]["running"] > 0:
                _ui_state["pools"][pool]["running"] -= 1
            _ui_state["pools"][pool]["done"] += 1
            if ok:
                _ui_state["pools"][pool]["ok"] += 1
            else:
                _ui_state["pools"][pool]["failed"] += 1

        job["status"] = "ok" if ok else "failed"
        job["exit_code"] = exit_code
        job["ended_at"] = time.time()
        job["note"] = note
        job["last_update"] = time.time()


def ui_set_global_status(status: str, stop_reason: Optional[str]) -> None:
    with _ui_lock:
        _ui_state["global_status"] = status
        _ui_state["stop_reason"] = stop_reason


def status_style(status: str) -> str:
    return {
        "queued": "bold white",
        "running": "bold cyan",
        "retry_wait": "bold yellow",
        "ok": "bold green",
        "failed": "bold red",
    }.get(status, "white")


def pool_style(pool: str) -> str:
    if pool == "llama-8b":
        return "magenta"
    if pool == "chatgpt-5-mini":
        return "blue"
    return "white"


def build_header_panel(state: Dict[str, Any]) -> Panel:
    elapsed = duration_str(time.time() - state["start_time"])

    title = Text()
    title.append("Planner Batch Dashboard", style="bold white")
    title.append("  ")
    title.append(f"[{state['global_status']}]", style=status_style(state["global_status"]))

    info = Table.grid(expand=True)
    info.add_column(ratio=1)
    info.add_column(ratio=1)
    info.add_column(ratio=1)
    info.add_column(ratio=2)

    info.add_row(
        f"Run ID: {state['run_id'] or '-'}",
        f"Elapsed: {elapsed}",
        f"Stop reason: {state['stop_reason'] or '-'}",
        ellipsize_middle(f"Logs: {state['log_dir'] or '-'}", 60),
    )

    return Panel(info, title=title, border_style="bright_white")


def build_pool_table(state: Dict[str, Any]) -> Panel:
    tbl = Table(expand=True)
    tbl.add_column("Pool", style="bold")
    tbl.add_column("Total", justify="right")
    tbl.add_column("Running", justify="right")
    tbl.add_column("Done", justify="right")
    tbl.add_column("OK", justify="right")
    tbl.add_column("Failed", justify="right")

    for pool_name, p in state["pools"].items():
        tbl.add_row(
            f"[{pool_style(pool_name)}]{pool_name}[/{pool_style(pool_name)}]",
            str(p["total"]),
            str(p["running"]),
            str(p["done"]),
            f"[green]{p['ok']}[/green]",
            f"[red]{p['failed']}[/red]",
        )

    return Panel(tbl, title="Pools", border_style="bright_white")


def build_runs_table(state: Dict[str, Any]) -> Panel:
    jobs = list(state["jobs"].values())
    jobs.sort(key=lambda j: (j["pool"], Path(j["cfg"]).name))

    tbl = Table(expand=True)
    tbl.add_column("Pool", no_wrap=True)
    tbl.add_column("Config")
    tbl.add_column("Status", no_wrap=True)
    tbl.add_column("Try", justify="right", no_wrap=True)
    tbl.add_column("Exit", justify="right", no_wrap=True)

    for j in jobs:
        status = f"[{status_style(j['status'])}]{j['status']}[/{status_style(j['status'])}]"
        attempt = "-"
        if j.get("attempt"):
            attempt = f"{j['attempt']}/{j['max_attempts']}"
        exit_code = "-" if j.get("exit_code") is None else str(j["exit_code"])

        tbl.add_row(
            f"[{pool_style(j['pool'])}]{j['pool']}[/{pool_style(j['pool'])}]",
            short_cfg_name(j["cfg"]),
            status,
            attempt,
            exit_code,
        )

    return Panel(tbl, title="All planners", border_style="bright_white")


def build_job_panel(job: Dict[str, Any]) -> Panel:
    cfg_name = short_cfg_name(job["cfg"])
    pool = job["pool"]
    status = job["status"]
    attempt = job.get("attempt", 0)
    max_attempts = job.get("max_attempts", 0)
    exit_code = job.get("exit_code")

    title = Text()
    title.append(pool, style=f"bold {pool_style(pool)}")
    title.append(" | ")
    title.append(cfg_name, style="bold white")
    title.append(" | ")
    title.append(status, style=status_style(status))
    if attempt:
        title.append(f" | try {attempt}/{max_attempts}", style="bold yellow")
    if exit_code is not None:
        title.append(f" | exit={exit_code}", style="bold")

    body_parts: List[Any] = []

    meta = Table.grid(expand=True)
    meta.add_column(ratio=1)
    meta.add_column(ratio=1)
    meta.add_row(
        f"Config: {ellipsize_middle(job['cfg'], 70)}",
        f"Log: {ellipsize_middle(job['log_path'] or '-', 70)}",
    )
    if job.get("note"):
        meta.add_row(f"Note: {job['note']}", "")
    body_parts.append(meta)

    log_path = job.get("log_path")
    lines: List[str] = []
    if log_path:
        lines = tail_lines(Path(log_path), TAIL_LINES_PER_JOB)

    log_text = Text()
    if not lines:
        log_text.append("(no log output yet)", style="dim")
    else:
        for line in lines:
            line = line.rstrip("\n")
            if re.search(r"\b(ERROR|Exception|Traceback|failed)\b", line, re.IGNORECASE):
                log_text.append(line + "\n", style="red")
            elif re.search(r"\b(WARN|warning)\b", line, re.IGNORECASE):
                log_text.append(line + "\n", style="yellow")
            elif re.search(r"\b(DONE|SUCCESS|ok)\b", line, re.IGNORECASE):
                log_text.append(line + "\n", style="green")
            else:
                log_text.append(line + "\n", style="white")

    body_parts.append(Panel(log_text, title="tail", border_style=pool_style(pool)))
    return Panel(Group(*body_parts), title=title, border_style=status_style(status))


def build_active_jobs_panel(state: Dict[str, Any]) -> Panel:
    jobs = list(state["jobs"].values())
    active = [j for j in jobs if j["status"] in {"running", "retry_wait", "queued"}]
    if not active:
        return Panel(Text("No active planners.", style="dim"), title="Active logs", border_style="bright_white")

    active.sort(key=lambda j: (j["pool"], Path(j["cfg"]).name))
    panels = [build_job_panel(j) for j in active[:MAX_ACTIVE_PANELS]]
    return Panel(Group(*panels), title="Active planners", border_style="bright_white")


def build_finished_jobs_panel(state: Dict[str, Any]) -> Panel:
    jobs = list(state["jobs"].values())
    finished = [j for j in jobs if j["status"] in {"ok", "failed"}]
    if not finished:
        return Panel(Text("No finished planners yet.", style="dim"), title="Finished logs", border_style="bright_white")

    finished.sort(
        key=lambda j: (
            0 if j["status"] == "failed" else 1,
            j["pool"],
            Path(j["cfg"]).name,
        )
    )
    panels = [build_job_panel(j) for j in finished[:MAX_FINISHED_PANELS]]
    return Panel(Group(*panels), title="Recently finished", border_style="bright_white")


def build_dashboard() -> Layout:
    with _ui_lock:
        state = json.loads(json.dumps(_ui_state))

    layout = Layout()
    layout.split_column(
        Layout(name="header", size=5),
        Layout(name="upper", size=12),
        Layout(name="middle", ratio=2),
        Layout(name="lower", ratio=2),
    )
    layout["upper"].split_row(
        Layout(name="pools", ratio=1),
        Layout(name="runs", ratio=2),
    )

    layout["header"].update(build_header_panel(state))
    layout["pools"].update(build_pool_table(state))
    layout["runs"].update(build_runs_table(state))
    layout["middle"].update(build_active_jobs_panel(state))
    layout["lower"].update(build_finished_jobs_panel(state))
    return layout


# -----------------------------
# Process-group tracking + killing
# -----------------------------
_running_lock = threading.Lock()
_running_procs: Dict[int, subprocess.Popen] = {}


def _register_proc(p: subprocess.Popen) -> None:
    with _running_lock:
        _running_procs[p.pid] = p


def _unregister_proc(p: subprocess.Popen) -> None:
    with _running_lock:
        _running_procs.pop(p.pid, None)


def _snapshot_running() -> List[subprocess.Popen]:
    with _running_lock:
        return list(_running_procs.values())


def kill_all_running() -> None:
    procs = _snapshot_running()
    if not procs:
        return

    for sig in (signal.SIGINT, signal.SIGTERM, signal.SIGKILL):
        for p in procs:
            if p.poll() is not None:
                continue
            try:
                os.killpg(p.pid, sig)
            except ProcessLookupError:
                pass
            except Exception:
                pass
        time.sleep(0.35)


# -----------------------------
# Runner
# -----------------------------
def start_process_in_own_pgroup(cmd: List[str], log_path: Path) -> Tuple[subprocess.Popen, Any]:
    log_path.parent.mkdir(parents=True, exist_ok=True)
    f = open(log_path, "wb")

    p = subprocess.Popen(
        cmd,
        stdout=f,
        stderr=subprocess.STDOUT,
        preexec_fn=os.setsid,
        close_fds=True,
    )
    _register_proc(p)
    return p, f


def run_one(pool: str, cfg: Path, log_dir: Path, summary: Dict[str, Any], stop_event: threading.Event) -> int:
    cfg_key = f"{pool}:{cfg.name}"
    job_id = cfg_key

    base = sanitize_name(cfg.stem)
    safe_pool = sanitize_name(pool)

    start_iso = iso_now()
    start_t = time.time()

    ui_job_created(job_id, pool, cfg)

    entry = {
        "pool": pool,
        "config": str(cfg),
        "start": start_iso,
        "end": None,
        "duration_sec": None,
        "exit_code": None,
        "status": "running",
        "attempts": [],
    }
    summary["runs"][cfg_key] = entry

    cmd = [str(RUNNER), "-c", str(cfg), "-l", LOG_LEVEL]
    max_attempts = 1 + (MAX_RETRIES_PER_CONFIG if RETRY_ON_TIMEOUT else 0)

    final_code: int = 1

    for attempt_num in range(1, max_attempts + 1):
        if stop_event.is_set():
            final_code = 1
            ui_job_finished(job_id, ok=False, exit_code=1, note="stopped")
            break

        if attempt_num == 1:
            out = log_dir / f"{safe_pool}__{base}.log"
        else:
            out = log_dir / f"{safe_pool}__{base}__try{attempt_num}.log"

        attempt_start_iso = iso_now()
        attempt_start_t = time.time()

        ui_job_attempt_start(job_id, attempt_num, max_attempts, out)

        p, fh = start_process_in_own_pgroup(cmd, out)

        code: int = 1
        try:
            while True:
                if stop_event.is_set():
                    try:
                        os.killpg(p.pid, signal.SIGINT)
                    except Exception:
                        pass
                    break

                polled = p.poll()
                if polled is not None:
                    break
                time.sleep(0.1)

            code = p.wait() if p.poll() is None else (p.returncode if p.returncode is not None else 1)

        except KeyboardInterrupt:
            try:
                os.killpg(p.pid, signal.SIGINT)
            except Exception:
                pass
            code = 130
        finally:
            _unregister_proc(p)
            try:
                fh.close()
            except Exception:
                pass

        attempt_end_iso = iso_now()
        attempt_dur = round(time.time() - attempt_start_t, 3)

        attempt_info = {
            "try": attempt_num,
            "log": str(out),
            "start": attempt_start_iso,
            "end": attempt_end_iso,
            "duration_sec": attempt_dur,
            "exit_code": int(code),
            "timeout_detected": False,
            "note": None,
        }

        if code == 0:
            attempt_info["note"] = "ok"
            entry["attempts"].append(attempt_info)
            final_code = 0
            ui_job_finished(job_id, ok=True, exit_code=0, note="ok")
            break

        timed_out = is_timeout_failure(out)
        attempt_info["timeout_detected"] = bool(timed_out)
        attempt_info["note"] = "timeout" if timed_out else "failed_non_timeout"
        entry["attempts"].append(attempt_info)

        if RETRY_ON_TIMEOUT and timed_out and attempt_num < max_attempts:
            if stop_event.is_set():
                final_code = int(code)
                ui_job_finished(job_id, ok=False, exit_code=final_code, note="stopped")
                break

            ui_job_retry_wait(job_id, "timeout detected, retrying soon")
            retry_idx = attempt_num
            backoff_sleep(retry_idx)
            continue

        final_code = int(code)
        ui_job_finished(
            job_id,
            ok=False,
            exit_code=final_code,
            note="timeout" if timed_out else "failed_non_timeout",
        )
        break

    end_iso = iso_now()
    dur = round(time.time() - start_t, 3)

    entry["end"] = end_iso
    entry["duration_sec"] = dur
    entry["exit_code"] = int(final_code)
    entry["status"] = "ok" if final_code == 0 else "failed"

    return int(final_code)


def run_pool(
    pool_name: str,
    cfg_dir: Path,
    parallel: int,
    log_dir: Path,
    summary: Dict[str, Any],
    stop_event: threading.Event,
) -> int:
    cfgs = sorted_configs(cfg_dir)
    if not cfgs:
        return 0

    in_flight: Dict[cf.Future[int], Path] = {}

    with cf.ThreadPoolExecutor(max_workers=parallel) as ex:
        idx = 0

        def submit_next() -> None:
            nonlocal idx
            if stop_event.is_set():
                return
            if idx >= len(cfgs):
                return
            cfg = cfgs[idx]
            idx += 1
            fut = ex.submit(run_one, pool_name, cfg, log_dir, summary, stop_event)
            in_flight[fut] = cfg

        for _ in range(min(parallel, len(cfgs))):
            submit_next()

        while in_flight:
            done, _ = cf.wait(in_flight.keys(), return_when=cf.FIRST_COMPLETED)

            for fut in done:
                cfg = in_flight.pop(fut)
                try:
                    code = fut.result()
                except KeyboardInterrupt:
                    stop_event.set()
                    kill_all_running()
                    return 1
                except Exception as e:
                    stop_event.set()
                    print(f"ERROR: {pool_name} crashed on {cfg}: {e}", file=sys.stderr)
                    kill_all_running()
                    return 1

                if code != 0:
                    stop_event.set()
                    kill_all_running()
                    return 1

                submit_next()

    return 0


def write_summary(summary_path: Path, summary: Dict[str, Any]) -> None:
    summary_path.parent.mkdir(parents=True, exist_ok=True)
    tmp = summary_path.with_suffix(".json.tmp")
    tmp.write_text(json.dumps(summary, indent=2), encoding="utf-8")
    tmp.replace(summary_path)


def main() -> int:
    run_id = ts_stamp()
    log_dir = LOGS_ROOT / run_id
    log_dir.mkdir(parents=True, exist_ok=True)

    summary_path = SUMMARY_DIR / SUMMARY_NAME

    llama_cfgs = sorted_configs(LLAMA_DIR)
    chatgpt_cfgs = sorted_configs(CHATGPT_DIR)

    ui_init(
        run_id=run_id,
        log_dir=log_dir,
        pool_totals={
            "llama-8b": len(llama_cfgs),
            "chatgpt-5-mini": len(chatgpt_cfgs),
        },
    )

    summary: Dict[str, Any] = {
        "run_id": run_id,
        "start": iso_now(),
        "end": None,
        "log_dir": str(log_dir),
        "log_level": LOG_LEVEL,
        "runner": str(RUNNER),
        "retry_policy": {
            "retry_on_timeout": RETRY_ON_TIMEOUT,
            "max_retries_per_config": MAX_RETRIES_PER_CONFIG,
            "backoff_sec": RETRY_BACKOFF_SEC,
            "backoff_mult": RETRY_BACKOFF_MULT,
            "timeout_patterns": TIMEOUT_PATTERNS,
        },
        "pools": {
            "llama-8b": {"dir": str(LLAMA_DIR), "parallel": LLAMA_PARALLEL},
            "chatgpt-5-mini": {"dir": str(CHATGPT_DIR), "parallel": CHATGPT_PARALLEL},
        },
        "stop_reason": None,
        "runs": {},
        "exit_code": None,
    }

    stop_event = threading.Event()
    exit_code = 0

    def handle_signal(signum, frame):
        summary["stop_reason"] = "signal"
        ui_set_global_status("failed", "signal")
        stop_event.set()
        kill_all_running()
        raise KeyboardInterrupt

    signal.signal(signal.SIGINT, handle_signal)
    signal.signal(signal.SIGTERM, handle_signal)

    try:
        with Live(
            build_dashboard(),
            console=console,
            screen=False,
            auto_refresh=False,
            vertical_overflow="crop",
            transient=False,
        ) as live:
            with cf.ThreadPoolExecutor(max_workers=2) as ex:
                f1 = ex.submit(run_pool, "llama-8b", LLAMA_DIR, LLAMA_PARALLEL, log_dir, summary, stop_event)
                f2 = ex.submit(run_pool, "chatgpt-5-mini", CHATGPT_DIR, CHATGPT_PARALLEL, log_dir, summary, stop_event)

                while True:
                    live.update(build_dashboard(), refresh=True)

                    if f1.done() and f2.done():
                        break

                    time.sleep(1.0 / UI_REFRESH_HZ)

                code1 = f1.result()
                code2 = f2.result()

                if code1 != 0 or code2 != 0:
                    summary["stop_reason"] = summary["stop_reason"] or "first_nonzero_exit"
                    ui_set_global_status("failed", summary["stop_reason"])
                    stop_event.set()
                    kill_all_running()
                    exit_code = 1
                else:
                    ui_set_global_status("ok", None)
                    exit_code = 0

                live.update(build_dashboard(), refresh=True)

    except KeyboardInterrupt:
        exit_code = 1
        summary["stop_reason"] = summary["stop_reason"] or "signal"
        ui_set_global_status("failed", summary["stop_reason"])
    except Exception as e:
        exit_code = 1
        summary["stop_reason"] = "exception"
        summary["exception"] = repr(e)
        ui_set_global_status("failed", "exception")
        kill_all_running()
    finally:
        summary["end"] = iso_now()
        summary["exit_code"] = exit_code
        write_summary(summary_path, summary)

    console.print()
    console.print(f"[bold green]Logs saved under:[/bold green] {log_dir}")
    console.print(f"[bold green]Summary written to:[/bold green] {summary_path}")
    return exit_code


if __name__ == "__main__":
    raise SystemExit(main())

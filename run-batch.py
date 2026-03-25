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
# -----------------------------
# Config
# -----------------------------
LOG_LEVEL = os.environ.get("LOG_LEVEL", "TRACE")
SCRIPT_DIR = Path(__file__).resolve().parent
RUNNER = (SCRIPT_DIR / "run-planner.sh").resolve()
LLAMA_DIR = (SCRIPT_DIR / "config-files/v1.8/a-star/llama-8b").resolve()
CHATGPT_DIR = (SCRIPT_DIR / "config-files/v1.8/a-star/chatgpt-5-mini").resolve()
RANDOM_DIR = (SCRIPT_DIR / "config-files/v1.8/a-star/random").resolve()
LOGS_ROOT = (SCRIPT_DIR / "planner-logs").resolve()
SUMMARY_DIR = (SCRIPT_DIR / "batch_execute").resolve()
LLAMA_PARALLEL = 1
CHATGPT_PARALLEL = 0
RANDOM_PARALLEL = 4
SUMMARY_NAME = "summary.json"
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
    items.sort(key=lambda t: t[0])
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
# -----------------------------
# Process-group tracking + killing
# -----------------------------
_running_lock = threading.Lock()
_running_procs: Dict[int, subprocess.Popen] = {}  # pid -> Popen
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
    base = sanitize_name(cfg.stem)
    safe_pool = sanitize_name(pool)
    start_iso = iso_now()
    start_t = time.time()
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
            break
        if attempt_num == 1:
            out = log_dir / f"{safe_pool}__{base}.log"
        else:
            out = log_dir / f"{safe_pool}__{base}__try{attempt_num}.log"
        attempt_start_iso = iso_now()
        attempt_start_t = time.time()
        print(
            f"==> START {dt.datetime.now():%F %T} | "
            f"pool={pool} | cfg={cfg} | try={attempt_num}/{max_attempts} | log={out}"
        )
        if stop_event.is_set():
            final_code = 1
            break
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
            print(f"==> DONE  {dt.datetime.now():%F %T} | cfg={cfg} | try={attempt_num} | exit=0")
            break
        timed_out = is_timeout_failure(out)
        attempt_info["timeout_detected"] = bool(timed_out)
        attempt_info["note"] = "timeout" if timed_out else "failed_non_timeout"
        entry["attempts"].append(attempt_info)
        print(
            f"==> DONE  {dt.datetime.now():%F %T} | "
            f"cfg={cfg} | try={attempt_num} | exit={code} | timeout={timed_out}"
        )
        if RETRY_ON_TIMEOUT and timed_out and attempt_num < max_attempts:
            if stop_event.is_set():
                final_code = int(code)
                break
            retry_idx = attempt_num
            print(f"==> RETRY {dt.datetime.now():%F %T} | cfg={cfg} | sleeping before retry...")
            backoff_sleep(retry_idx)
            continue
        final_code = int(code)
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
    print(f"---- Pool: {pool_name} | dir={cfg_dir} | parallel={parallel} ----")
    cfgs = sorted_configs(cfg_dir)
    if not cfgs:
        print(f"WARN: No configs found in {cfg_dir}", file=sys.stderr)
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
            "llama-8b":       {"dir": str(LLAMA_DIR),   "parallel": LLAMA_PARALLEL},
            "chatgpt-5-mini": {"dir": str(CHATGPT_DIR), "parallel": CHATGPT_PARALLEL},
            "random":         {"dir": str(RANDOM_DIR),  "parallel": RANDOM_PARALLEL},
        },
        "stop_reason": None,
        "runs": {},
        "exit_code": None,
    }
    stop_event = threading.Event()
    exit_code = 0
    def handle_signal(signum, frame):
        summary["stop_reason"] = "signal"
        stop_event.set()
        kill_all_running()
        raise KeyboardInterrupt
    signal.signal(signal.SIGINT, handle_signal)
    signal.signal(signal.SIGTERM, handle_signal)
    try:
        with cf.ThreadPoolExecutor(max_workers=3) as ex:
            f1 = ex.submit(run_pool, "llama-8b",       LLAMA_DIR,   LLAMA_PARALLEL,   log_dir, summary, stop_event)
            f2 = ex.submit(run_pool, "chatgpt-5-mini", CHATGPT_DIR, CHATGPT_PARALLEL, log_dir, summary, stop_event)
            f3 = ex.submit(run_pool, "random",         RANDOM_DIR,  RANDOM_PARALLEL,  log_dir, summary, stop_event)
            done, pending = cf.wait({f1, f2, f3}, return_when=cf.FIRST_COMPLETED)
            for f in done:
                code = f.result()
                if code != 0:
                    summary["stop_reason"] = summary["stop_reason"] or "first_nonzero_exit"
                    stop_event.set()
                    kill_all_running()
                    exit_code = 1
            for f in pending:
                code = f.result()
                if code != 0:
                    summary["stop_reason"] = summary["stop_reason"] or "first_nonzero_exit"
                    stop_event.set()
                    kill_all_running()
                    exit_code = 1
    except KeyboardInterrupt:
        exit_code = 1
        summary["stop_reason"] = summary["stop_reason"] or "signal"
    except Exception as e:
        exit_code = 1
        summary["stop_reason"] = "exception"
        summary["exception"] = repr(e)
        kill_all_running()
    finally:
        summary["end"] = iso_now()
        summary["exit_code"] = exit_code
        write_summary(summary_path, summary)
    print(f"Logs saved under: {log_dir}")
    print(f"Summary written to: {summary_path}")
    return exit_code
if __name__ == "__main__":
    raise SystemExit(main())

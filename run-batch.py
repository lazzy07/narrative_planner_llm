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
# Config (edit to match your repo)
# -----------------------------
LOG_LEVEL = os.environ.get("LOG_LEVEL", "TRACE")

SCRIPT_DIR = Path(__file__).resolve().parent
RUNNER = (SCRIPT_DIR / "run-planner.sh").resolve()
LLAMA_DIR = (SCRIPT_DIR / "config-files/a-star/llama-8b").resolve()
CHATGPT_DIR = (SCRIPT_DIR / "config-files/a-star/chatgpt-5-mini").resolve()
LOGS_ROOT = (SCRIPT_DIR / "planner-logs").resolve()
SUMMARY_DIR = (SCRIPT_DIR / "batch_execute").resolve()

LLAMA_PARALLEL = 1
CHATGPT_PARALLEL = 3

SUMMARY_NAME = "summary.json"

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
    # Simple // comment stripper (like your sed)
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

    # Escalate: INT -> TERM -> KILL
    for sig in (signal.SIGINT, signal.SIGTERM, signal.SIGKILL):
        for p in procs:
            if p.poll() is not None:
                continue
            try:
                os.killpg(p.pid, sig)  # pgid == pid because we use setsid()
            except ProcessLookupError:
                pass
            except Exception:
                pass
        # small grace between escalations
        time.sleep(0.35)


# -----------------------------
# Runner
# -----------------------------
def start_process_in_own_pgroup(cmd: List[str], log_path: Path) -> subprocess.Popen:
    log_path.parent.mkdir(parents=True, exist_ok=True)
    f = open(log_path, "wb")

    # New session => new process group; pgid == pid. Then os.killpg(pid, SIG..) works reliably.
    p = subprocess.Popen(
        cmd,
        stdout=f,
        stderr=subprocess.STDOUT,
        preexec_fn=os.setsid,
        close_fds=True,
    )
    _register_proc(p)
    return p


def run_one(pool: str, cfg: Path, log_dir: Path, summary: Dict[str, Any], stop_event: threading.Event) -> int:
    cfg_key = f"{pool}:{cfg.name}"
    base = cfg.stem
    out = log_dir / f"{base}.log"

    start_iso = iso_now()
    start_t = time.time()

    entry = {
        "pool": pool,
        "config": str(cfg),
        "log": str(out),
        "start": start_iso,
        "end": None,
        "duration_sec": None,
        "exit_code": None,
        "status": "running",
    }
    summary["runs"][cfg_key] = entry

    print(f"==> START {dt.datetime.now():%F %T} | {cfg} | log={out}")

    cmd = [str(RUNNER), "-c", str(cfg), "-l", LOG_LEVEL]
    p = start_process_in_own_pgroup(cmd, out)

    try:
        # If someone else already failed, don’t wait forever; let kill_all_running handle it.
        while True:
            if stop_event.is_set():
                # someone failed elsewhere; we’re aborting
                try:
                    os.killpg(p.pid, signal.SIGINT)
                except Exception:
                    pass
                break

            code = p.poll()
            if code is not None:
                break
            time.sleep(0.1)

        code = p.wait() if p.poll() is None else p.returncode

    except KeyboardInterrupt:
        # Ensure this child dies too
        try:
            os.killpg(p.pid, signal.SIGINT)
        except Exception:
            pass
        code = 130
    finally:
        _unregister_proc(p)

    end_iso = iso_now()
    dur = round(time.time() - start_t, 3)

    entry["end"] = end_iso
    entry["duration_sec"] = dur
    entry["exit_code"] = int(code) if code is not None else None

    if code == 0:
        entry["status"] = "ok"
    else:
        entry["status"] = "failed"

    print(f"==> DONE  {dt.datetime.now():%F %T} | {cfg} | exit={code} | log={out}")
    return int(code) if code is not None else 1


def run_pool(pool_name: str, cfg_dir: Path, parallel: int, log_dir: Path,
             summary: Dict[str, Any], stop_event: threading.Event) -> int:
    print(f"---- Pool: {pool_name} | dir={cfg_dir} | parallel={parallel} ----")

    cfgs = sorted_configs(cfg_dir)
    if not cfgs:
        print(f"WARN: No configs found in {cfg_dir}", file=sys.stderr)
        return 0

    # We manage a rolling window of futures so we can stop ASAP on first failure.
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

        # Prime the queue
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
                    # treat exceptions as failure
                    stop_event.set()
                    print(f"ERROR: {pool_name} crashed on {cfg}: {e}", file=sys.stderr)
                    kill_all_running()
                    return 1

                if code != 0:
                    # FIRST non-zero exit => stop everything
                    stop_event.set()
                    kill_all_running()
                    return 1

                # Success => keep pipeline full
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
        "pools": {
            "llama-8b": {"dir": str(LLAMA_DIR), "parallel": LLAMA_PARALLEL},
            "chatgpt-5-mini": {"dir": str(CHATGPT_DIR), "parallel": CHATGPT_PARALLEL},
        },
        "stop_reason": None,  # "first_nonzero_exit" | "signal" | "exception"
        "runs": {},           # key -> entry
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
        # Run both pools concurrently; if either fails, we stop both via stop_event + kill_all_running.
        with cf.ThreadPoolExecutor(max_workers=2) as ex:
            f1 = ex.submit(run_pool, "llama-8b", LLAMA_DIR, LLAMA_PARALLEL, log_dir, summary, stop_event)
            f2 = ex.submit(run_pool, "chatgpt-5-mini", CHATGPT_DIR, CHATGPT_PARALLEL, log_dir, summary, stop_event)

            # Wait for first pool to finish/fail; if failure, trigger stop and kill.
            done, pending = cf.wait({f1, f2}, return_when=cf.FIRST_COMPLETED)

            # If the first finished is failure, stop the other immediately.
            for f in done:
                code = f.result()
                if code != 0:
                    summary["stop_reason"] = summary["stop_reason"] or "first_nonzero_exit"
                    stop_event.set()
                    kill_all_running()
                    exit_code = 1

            # If not failed yet, wait for the other. If it fails, stop.
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

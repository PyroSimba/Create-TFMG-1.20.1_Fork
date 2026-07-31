"""Automated TFMG dev-server test harness.

Usage: python tests/test_harness.py [cases.json]

Starts `gradlew runServer`, waits for boot, drives cases via RCON, tails
run/logs/latest.log for exceptions, prints one JSON summary to stdout
(and writes tests/result.json). Exit code 0 = all pass.

Case format (tests/cases.json):
  {"name": str,
   "command": str,                  # RCON command (or use "action": "restart")
   "expect_response": str|null,     # regex on RCON response
   "expect_log": str|null,          # regex expected in new log lines (within log_timeout s)
   "delay": float,                  # seconds to wait after command (default 0.5)
   "allow_exception": bool}         # default false: any new Exception/Error in log fails the case
Special action: {"name": ..., "action": "restart"} -> save-all, stop, relaunch server.
"""
import json
import os
import re
import subprocess
import sys
import time
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
RUN_DIR = REPO / "run"
LOG = RUN_DIR / "logs" / "latest.log"
RCON_PORT = 25575
RCON_PASS = "tfmgtest"
BOOT_TIMEOUT = 600  # first boot generates world + loads mods, slow
LOG_TIMEOUT_DEFAULT = 10

sys.path.insert(0, str(Path(__file__).parent))
from rcon import Rcon, RconError

EXCEPTION_RE = re.compile(r"(Exception|java\.lang\.\w*Error)[:\s]")
# lines that contain 'Exception' but are noise (e.g. mod warnings at boot)
IGNORE_RE = re.compile(r"DatafixerException|ModResolutionException: no|Skipping bad option")


class LogTail:
    def __init__(self, path):
        self.path = path
        self.pos = 0

    def reset_to_end(self):
        self.pos = self.path.stat().st_size if self.path.exists() else 0

    def new_lines(self):
        if not self.path.exists():
            return []
        if self.path.stat().st_size < self.pos:  # log rotated on server restart
            self.pos = 0
        with open(self.path, "r", encoding="utf-8", errors="replace") as f:
            f.seek(self.pos)
            data = f.read()
            self.pos = f.tell()
        return data.splitlines()


def start_server():
    gradlew = str(REPO / ("gradlew.bat" if os.name == "nt" else "gradlew"))
    proc = subprocess.Popen(
        [gradlew, "runServer", "--console=plain"],
        cwd=str(REPO),
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    return proc


def wait_for_boot(tail, proc, timeout=BOOT_TIMEOUT):
    deadline = time.time() + timeout
    boot_lines = []
    while time.time() < deadline:
        if proc.poll() is not None:
            return False, boot_lines[-50:]
        for line in tail.new_lines():
            boot_lines.append(line)
            if re.search(r"Done \([\d.]+s\)!", line):
                return True, []
        time.sleep(2)
    return False, boot_lines[-50:]


def connect_rcon(retries=10):
    for _ in range(retries):
        try:
            return Rcon(port=RCON_PORT, password=RCON_PASS)
        except (OSError, RconError):
            time.sleep(2)
    raise RconError("could not connect to RCON")


def stop_server(proc, rc):
    try:
        if rc:
            rc.cmd("stop")
    except (OSError, RconError):
        pass
    try:
        proc.wait(timeout=120)
    except subprocess.TimeoutExpired:
        proc.kill()
        proc.wait(timeout=30)


def scan_exceptions(lines):
    hits = []
    for i, line in enumerate(lines):
        if EXCEPTION_RE.search(line) and not IGNORE_RE.search(line):
            hits.append("\n".join(lines[i : i + 15]))  # include stack trace slice
    return hits


def run_case(case, rc, tail, proc):
    result = {"name": case["name"], "status": "pass", "detail": None}
    tail.new_lines()  # flush pre-case log noise

    if case.get("action") == "restart":
        try:
            rc.cmd("save-all flush")
            time.sleep(2)
        except (OSError, RconError):
            pass
        stop_server(proc["proc"], rc)
        time.sleep(5)  # let the port release
        proc["proc"] = start_server()
        tail.pos = tail.path.stat().st_size if tail.path.exists() else 0  # old log; rotation detected in new_lines
        ok, boot_tail = wait_for_boot(tail, proc["proc"])
        if not ok:
            result.update(status="fail", detail={"reason": "server failed to restart", "log": boot_tail})
            return result, None
        new_rc = connect_rcon()
        return result, new_rc

    resp = rc.cmd(case["command"])
    time.sleep(case.get("delay", 0.5))
    lines = tail.new_lines()

    exp_resp = case.get("expect_response")
    if exp_resp and not re.search(exp_resp, resp, re.DOTALL):
        result.update(status="fail", detail={"reason": "response mismatch", "expected": exp_resp, "actual": resp[:2000]})
        return result, rc

    exp_log = case.get("expect_log")
    if exp_log:
        deadline = time.time() + case.get("log_timeout", LOG_TIMEOUT_DEFAULT)
        found = any(re.search(exp_log, l) for l in lines)
        while not found and time.time() < deadline:
            time.sleep(1)
            lines += tail.new_lines()
            found = any(re.search(exp_log, l) for l in lines)
        if not found:
            result.update(status="fail", detail={"reason": "log pattern not found", "expected": exp_log, "log": lines[-30:]})
            return result, rc

    if not case.get("allow_exception", False):
        exceptions = scan_exceptions(lines)
        if exceptions:
            result.update(status="fail", detail={"reason": "exception in log", "exceptions": exceptions[:3]})
            return result, rc

    result["detail"] = {"response": resp[:500]}
    return result, rc


def main():
    cases_file = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).parent / "cases.json"
    cases = json.loads(cases_file.read_text(encoding="utf-8"))

    tail = LogTail(LOG)
    tail.reset_to_end()

    summary = {"boot": "pending", "results": [], "passed": 0, "failed": 0}
    proc_holder = {"proc": start_server()}
    rc = None
    try:
        ok, boot_tail = wait_for_boot(tail, proc_holder["proc"])
        if not ok:
            summary["boot"] = "fail"
            summary["boot_log"] = boot_tail
            return summary
        summary["boot"] = "ok"
        rc = connect_rcon()

        for case in cases:
            if rc is None:
                summary["results"].append({"name": case["name"], "status": "skip", "detail": {"reason": "server down"}})
                summary["failed"] += 1
                continue
            try:
                result, rc = run_case(case, rc, tail, proc_holder)
            except Exception as e:
                result = {"name": case["name"], "status": "fail", "detail": {"reason": f"harness error: {e}"}}
            summary["results"].append(result)
            summary["passed" if result["status"] == "pass" else "failed"] += 1
    finally:
        stop_server(proc_holder["proc"], rc)
    return summary


if __name__ == "__main__":
    summary = main()
    out = json.dumps(summary, indent=2)
    (Path(__file__).parent / "result.json").write_text(out, encoding="utf-8")
    print(out)
    sys.exit(0 if summary.get("boot") == "ok" and summary["failed"] == 0 else 1)

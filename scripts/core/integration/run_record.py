#!/usr/bin/env python3
"""Run one exact command, retain literal stdout/stderr and exit status in audit JSON.

Usage: run_record.py LABEL -- COMMAND ARG ...
The parent coordinator must grant the exclusive Gradle window before use with Gradle.
"""
import datetime
import json
import os
from pathlib import Path
import subprocess
import sys
import time

ROOT = next(path for path in Path(__file__).resolve().parents
            if (path / "gradlew").is_file() and (path / "core-bindings/core.lock.json").is_file())
AUDIT = ROOT / "build/core-adoption-integration-audit"


def main():
    label = sys.argv[1]
    assert sys.argv[2] == "--"
    command = sys.argv[3:]
    assert command
    AUDIT.mkdir(parents=True, exist_ok=True)
    log = AUDIT / "logs" / f"{label}.log"
    log.parent.mkdir(exist_ok=True)
    start = time.time()
    with log.open("w") as stream:
        try:
            result = subprocess.run(command, cwd=ROOT, stdout=stream, stderr=subprocess.STDOUT)
            exit_status = result.returncode
            spawn_failure = None
        except OSError as failure:
            # No command ran: distinguish an execution failure from a process exit.
            exit_status = None
            spawn_failure = str(failure)
            stream.write(f"Command launch failed: {failure}\n")
    record = {
        "label": label,
        "cwd": str(ROOT),
        "argv": command,
        "environment": {k: os.environ[k] for k in ["JAVA_HOME", "ANDROID_HOME", "ANDROID_SDK_ROOT", "RUSTUP_HOME", "CARGO_HOME", "CARGO", "RUSTC", "RUSTUP_TOOLCHAIN"] if k in os.environ},
        "started_utc": datetime.datetime.fromtimestamp(start, datetime.timezone.utc).isoformat(),
        "duration_seconds": round(time.time() - start, 3),
        "exit_status": exit_status,
        "spawn_failure": spawn_failure,
        "literal_combined_output": log.read_text(),
        "log": str(log),
    }
    (AUDIT / f"{label}.json").write_text(json.dumps(record, ensure_ascii=False, indent=2) + "\n")
    print(f"{label}: exit_status={exit_status}; literal output={log}")
    print(record["literal_combined_output"][-10000:])
    return exit_status if exit_status is not None else 127


if __name__ == "__main__":
    sys.exit(main())

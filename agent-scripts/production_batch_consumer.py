#!/usr/bin/env python3
"""Production registrar parity checks inside the published-consumer boundary."""

import json
from pathlib import Path
import re
import subprocess
import sys


def main():
    root, consumer, repo, work = map(Path, sys.argv[1:])
    fixture = root / "formver.compiler-plugin/testData/diagnostics/language/soundness/file_batch.kt"
    source = re.sub(r"<![^>]*!>|<!>", "", fixture.read_text())
    target = consumer / "src/main/kotlin/Batch.kt"
    target.parent.mkdir(parents=True)
    measurements = []
    # Each invocation starts a fresh compiler JVM, disables incremental/build/outcome
    # caches, and compiles the same source. Dependency and OS caches remain warm.
    for variant in ("negative", "positive"):
        text = source
        if variant == "positive":
            text = text.replace("verify(false)", "verify(true)")
            text = text.replace("result && !result", "result")
            text = text.replace("verify(impossible && !impossible)", "verify(impossible)")
        target.write_text(text)
        baseline = None
        for workers in ("0", "1", "2", "default"):
            label = f"batch-{variant}-{workers}"
            log = work / f"{label}.log"
            command = [sys.executable, str(root / "agent-scripts/gate_measure.py"),
                       f"consumer.{label}", str(root / "gradlew"), "--no-daemon",
                       "-p", str(consumer), f"-Dformver.repo=file://{repo}",
                       "--rerun-tasks", "compileKotlin"]
            if workers != "default":
                command.append(f"-Pworkers={workers}")
            with log.open("w") as output:
                result = subprocess.run(command, stdout=output, stderr=subprocess.STDOUT)
            output = log.read_text()
            if (result.returncode == 0) != (variant == "positive"):
                raise RuntimeError(f"{label}: unexpected exit {result.returncode}\n{output}")
            diagnostics = sorted(re.findall(r"^([ew]): .*Batch\.kt:(.*)$", output, re.MULTILINE))
            errors = re.findall(r"^e: .*Batch\.kt:(\d+):(\d+)\b", output, re.MULTILINE)
            if variant == "negative":
                # Unsuppressed false assertions must survive, including those owned
                # by local functions and lifted lambdas.
                expected = {str(i) for i, line in enumerate(text.splitlines(), 1) if "verify(false)" in line}
                observed = {line for line, _ in errors}
                if not expected <= observed:
                    raise RuntimeError(f"{label}: missing assertion locations {expected - observed}\n{output}")
            suppressed = {str(i) for i, line in enumerate(text.splitlines(), 1) if "verify(1 == 0)" in line}
            if any(line in suppressed for line, _ in errors):
                raise RuntimeError(f"{label}: suppressed assertion reported\n{output}")
            if baseline is None:
                baseline = diagnostics
            elif diagnostics != baseline:
                raise RuntimeError(f"{label}: diagnostic parity failed\n{baseline!r}\n{diagnostics!r}\n{output}")
            if variant == "positive" and not (consumer / "build/classes/kotlin/main/BatchKt.class").is_file():
                raise RuntimeError(f"{label}: no BatchKt.class")
            for line in output.splitlines():
                if line.startswith('{"stage":'):
                    measurements.append(json.loads(line))
            print(f"production batch consumer: {label} passed", flush=True)
    (work / "production-batch-measurements.json").write_text(json.dumps(measurements, indent=2) + "\n")


if __name__ == "__main__":
    main()

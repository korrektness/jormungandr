#!/usr/bin/env python3
"""Measure command boundaries without capturing their output (JSONL on stderr)."""

import hashlib
import json
import os
from pathlib import Path
import platform
import shutil
import subprocess
import sys
import time


def provenance():
    def git(*args):
        return subprocess.check_output(['git', *args], text=True).strip()

    tools = {}
    for tool, flag in (('java', '-version'), ('z3', '-version'), ('python3', '--version')):
        path = os.environ.get('Z3_EXE') if tool == 'z3' else None
        path = path or shutil.which(tool)
        tools[tool] = dict(path=path)
        if path:
            result = subprocess.run([path, flag], stdout=subprocess.PIPE,
                                    stderr=subprocess.STDOUT, text=True, check=False)
            tools[tool].update(version=result.stdout, exit_status=result.returncode)
    files = ('gradle/wrapper/gradle-wrapper.properties',
             'gradle/gradle-daemon-jvm.properties', 'gradle.properties')
    return dict(commit=git('rev-parse', 'HEAD'), dirty=git('status', '--porcelain'),
                platform=platform.platform(), python=sys.version,
                tools=tools,
                configuration={name: Path(name).read_text() if Path(name).exists() else None
                               for name in files},
                wrapper_sha256=hashlib.sha256(Path('gradlew').read_bytes()).hexdigest(),
                environment={key: value for key, value in os.environ.items()
                             if key.startswith('SNAKT_')
                             or key in ('GRADLE_USER_HOME', 'GRADLE_OPTS', 'JAVA_HOME',
                                        'JAVA_OPTS', 'Z3_EXE', 'KOTLIN_OPTS',
                                        'JDK_JAVA_OPTIONS', 'XDG_CACHE_HOME', 'HOME')})


def measure(stage, command, *, emit, clock=time.monotonic_ns, run=subprocess.call):
    start = clock()
    error = None
    try:
        status = run(command)
    except OSError as failure:
        status = 127 if isinstance(failure, FileNotFoundError) else 126
        error = str(failure)
    # Match the shell's status for a child terminated by a signal.
    status = status if status >= 0 else 128 - status
    record = dict(stage=stage, command=command, cwd=os.getcwd(),
                  elapsed_ns=clock() - start, exit_status=status)
    if error:
        record['error'] = error
    try:
        emit(record)
    except OSError as failure:
        # Reporting must surface failure without replacing a failed command's status.
        if not status:
            raise
        print(f'{stage}: {failure}', file=sys.stderr)
    return status


def main(argv=None, *, clock=time.monotonic_ns):
    stage, *command = sys.argv[1:] if argv is None else argv
    directory = os.environ.get('SNAKT_GATE_ARTIFACTS')
    output = Path(directory) if directory else None
    if output:
        output = output.resolve()
        if output == Path.cwd() or Path.cwd() in output.parents:
            raise ValueError(f'Artifact directory is inside checkout: {output}')
        output.mkdir(parents=True, exist_ok=True)
        if stage in ('gate.total', 'consumer.publish'):
            (output / (stage + '.provenance.json')).write_text(json.dumps(provenance(), indent=2) + '\n')

    def emit(record):
        line = json.dumps(record) + '\n'
        sys.stderr.write(line)
        if output:
            # Each boundary owns a file, including nested commands.
            (output / (stage + '.json')).write_text(line)

    return measure(stage, command, emit=emit, clock=clock)


if __name__ == '__main__':
    sys.exit(main())

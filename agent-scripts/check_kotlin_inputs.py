#!/usr/bin/env python3
"""Reconcile Kotlin task sources with the files seen by FIR, not proof reports."""

import argparse
import hashlib
import json
from pathlib import Path
import re
import sys


COMPILATION = re.compile(r"(?::[A-Za-z0-9_-][A-Za-z0-9_.-]*)*:compileKotlin")
SHA256 = re.compile(r"[0-9a-f]{64}")


class InputProblem(ValueError):
    def __init__(self, problem, **data):
        self.details = dict(problem=problem, **data)


def lines(path):
    try:
        contents = path.read_bytes().decode("utf-8")
    except FileNotFoundError as error:
        raise InputProblem("missing_manifest", manifest=str(path)) from error
    except (OSError, UnicodeError) as error:
        raise InputProblem("unreadable_manifest", manifest=str(path), error=str(error)) from error
    if "\r" in contents:
        raise InputProblem("malformed_manifest", manifest=str(path), reason="carriage_return")
    if not contents.endswith("\n"):
        raise InputProblem("malformed_manifest", manifest=str(path), reason="missing_final_newline")
    return contents[:-1].split("\n")


def file_rows(path, rows, compiler=False):
    result = {}
    for row in rows:
        fields = row.split("\t")
        if not compiler:
            fields = ["source", *fields, None]
        if len(fields) != 4:
            raise InputProblem("malformed_row", manifest=str(path), row=row)
        kind, filename, digest, normalized = fields
        source = Path(filename)
        if kind != "source":
            raise InputProblem("unexpected_file_kind", manifest=str(path), path=filename, kind=kind)
        if (not source.is_absolute() or "\x00" in filename or not SHA256.fullmatch(digest)
                or (compiler and not SHA256.fullmatch(normalized))):
            raise InputProblem("malformed_row", manifest=str(path), row=row)
        if source in result:
            raise InputProblem("duplicate_file", manifest=str(path), path=filename)
        result[source] = digest, normalized
    return result


def resolve_source(path, digest, normalized, compilation):
    try:
        real = path.resolve(strict=True)
        raw = real.read_bytes()
        text = raw.decode("utf-8-sig").replace("\r\n", "\n").replace("\r", "\n")
    except (OSError, RuntimeError, UnicodeError) as error:
        raise InputProblem("unreadable_source", compilation=compilation, path=str(path), error=str(error)) from error
    observed = hashlib.sha256(raw).hexdigest()
    if real != path:
        raise InputProblem("source_path_changed", compilation=compilation, path=str(path), resolved=str(real))
    if observed != digest:
        raise InputProblem("changed_source", compilation=compilation, path=str(path), digest=digest, observed=observed)
    text_digest = hashlib.sha256(text.encode("utf-8")).hexdigest()
    if text_digest != normalized:
        raise InputProblem("compiler_text_mismatch", compilation=compilation, path=str(path),
                           expected=text_digest, observed=normalized)
    return real


def reconcile(run, checkout):
    expected_path = run / "expected.tsv"
    header, *ids = lines(expected_path)
    invalid_ids = [compilation for compilation in ids if not COMPILATION.fullmatch(compilation)]
    if header != "production-kotlin-v1" or not ids or invalid_ids:
        raise InputProblem("invalid_registry", manifest=str(expected_path), header=header,
                           row_count=len(ids), invalid_ids=invalid_ids)
    if len(ids) != len(set(ids)):
        raise InputProblem("duplicate_compilation", manifest=str(expected_path), compilations=ids)

    task_dirs = {run.joinpath(*i[1:].split(":")): i for i in ids}
    for name in ("task.tsv", "compiler.tsv"):
        for path in sorted(run.rglob(name)):
            if path.parent not in task_dirs:
                raise InputProblem("unexpected_manifest", manifest=str(path))

    result = {}
    for directory, compilation in sorted(task_dirs.items()):
        task, compiler = directory / "task.tsv", directory / "compiler.tsv"
        header, *rows = lines(task)
        if header == "task-inputs-v1\tconfirmed-empty" and not rows:
            if compiler.exists() or compiler.is_symlink():
                raise InputProblem("compiler_ran_for_empty_task", compilation=compilation, manifest=str(compiler))
            result[compilation] = dict(state="confirmed-empty", files=[])
            continue
        if header != "task-inputs-v1\tsources" or not rows:
            raise InputProblem("malformed_manifest", compilation=compilation, manifest=str(task),
                               header=header, row_count=len(rows))
        task_files = file_rows(task, rows)
        header, *rows = lines(compiler)
        if header != "compiler-inputs-v1":
            raise InputProblem("malformed_manifest", compilation=compilation, manifest=str(compiler), header=header)
        compiler_files = file_rows(compiler, rows, compiler=True)
        missing = task_files.keys() - compiler_files.keys()
        extra = compiler_files.keys() - task_files.keys()
        changed = {}
        for path in sorted(task_files.keys() & compiler_files.keys()):
            task_digest, _ = task_files[path]
            compiler_digest, _ = compiler_files[path]
            if task_digest != compiler_digest:
                changed[str(path)] = dict(task_sha256=task_digest, compiler_sha256=compiler_digest)
        if missing or extra or changed:
            raise InputProblem("input_mismatch", compilation=compilation,
                               missing=sorted(map(str, missing)), extra=sorted(map(str, extra)), changed=changed)
        files = []
        for path, (digest, normalized) in sorted(compiler_files.items()):
            real = resolve_source(path, digest, normalized, compilation)
            if not real.is_relative_to(checkout):
                raise InputProblem("unknown_logical_root", compilation=compilation, path=str(path), checkout=str(checkout))
            files.append(dict(root="checkout", path=real.relative_to(checkout).as_posix(), sha256=digest))
        result[compilation] = dict(state="sources", files=files)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("run", type=Path)
    parser.add_argument("checkout", type=Path)
    args = parser.parse_args()
    try:
        result = reconcile(args.run, args.checkout.resolve(strict=True))
    except InputProblem as error:
        print(json.dumps(error.details), file=sys.stderr)
        return 1
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

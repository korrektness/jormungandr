#!/usr/bin/env python3
"""Store maintenance and test-run reporting for the verification cache."""

import pathlib
import sys
import time


def prune(store, now):
    store.mkdir(parents=True, exist_ok=True)
    stamp = store / '.pruned'
    if stamp.exists() and now - stamp.stat().st_mtime < 86400:
        return
    for entry in store.glob('*/*'):
        try:
            modified = entry.stat().st_mtime
        except FileNotFoundError:
            # Another maintenance process can remove an expired entry first.
            continue
        if entry.suffix in ('.success', '.failure') and now - modified > 30 * 86400:
            entry.unlink(missing_ok=True)
    stamp.touch()
    import os
    os.utime(stamp, (now, now))


def summary(directory):
    hits = misses = corrupt = 0
    seconds = 0.0
    for report in directory.glob('*.tsv'):
        _, event, elapsed = report.read_text().strip().split('\t')
        hits += event == 'hit'
        misses += event == 'miss'
        corrupt += event == 'corrupt'
        if event == 'miss':
            seconds += float(elapsed)
    print(f'Verification: {hits} programs from cache, {misses} verified '
          f'({seconds:.3f} s of Silicon time), {corrupt} corrupt entries replaced.')


if __name__ == '__main__':
    if sys.argv[1] == 'prune':
        prune(pathlib.Path(sys.argv[2]), time.time())
    elif sys.argv[1] == 'summary':
        summary(pathlib.Path(sys.argv[2]))
    else:
        raise ValueError(sys.argv[1])

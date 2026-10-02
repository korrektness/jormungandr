# AGENTS.md

Tests are golden-file based: a test passes when its goldens match. Regenerating
records whatever the run produced, except a change in whether a function
verifies, fails or is skipped: that fails the test and writes nothing. Read what
`--update-goldens` prints.

    ./agent-scripts/test.sh [pattern]                  # conversion only — the fast loop
    ./agent-scripts/test.sh --verify [pattern]         # full pipeline, including verification
    ./agent-scripts/test.sh --verify-changed [pattern] # verify only where conversion output changed
    ./agent-scripts/test.sh --update-goldens [pattern] # regenerate goldens, then report what changed
    ./agent-scripts/test.sh --update-goldens --record-outcomes <pattern>
                                                       # also record verification outcome changes

A pattern is the testData file's name, the path to it, or the generated method
name. A failing run prints the expected/actual diff.

Verification is slow. Stay on the fast loop while developing.

Before pushing:

    ./agent-scripts/check-all.sh

Exit 1 is a real failure. Exit 2 means a check did not run: install what it
needs and run again, rather than reading 2 as a pass.

docs/agents-dev.md goes deeper on all of the above. Written for humans:
README.md, docs/developing.md, SPECIFICATIONS.md.

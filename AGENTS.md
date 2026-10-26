# AGENTS.md

Tests are golden-file based: a test passes when its goldens match. Regenerating
records whatever the run produced, except a change in whether a function
verifies, fails or is skipped: that fails the test and writes nothing. Read what
`--update-goldens` prints.

    ./agent-scripts/test.sh [pattern]                  # conversion only — the fast loop
    ./agent-scripts/test.sh --verify [pattern]         # full pipeline, including verification
    ./agent-scripts/test.sh --verify-changed [pattern] # verify only where conversion output changed
    ./agent-scripts/test.sh --update-goldens [pattern] # full pipeline + regeneration; see docs/agents-dev.md
    ./agent-scripts/test.sh --update-goldens --record-outcomes <pattern>
                                                       # also record verification outcome changes
    ./agent-scripts/test.sh --vacuity [pattern]        # list verified methods whose context is contradictory

A pattern is the testData file's name, the path to it, or the generated method
name. A failing run prints the expected/actual diff.

Verification is slow. Stay on the fast loop while developing.

The land gate verifies only the tests whose testData a change touches; the
nightly run verifies everything (docs/agents-dev.md, "Landing and nightly").
Before landing a change that can alter verification without changing a golden
(the `viper` module, `formver.compiler-plugin/test-fixtures`, Silicon or Z3, the
Gradle plugin or packaging), run what it affects:

    ./agent-scripts/test.sh --verify [pattern]
    ./agent-scripts/check-all.sh                       # everything the nightly runs

Exit 1 is a real failure. Exit 2 means a check did not run: install what it
needs and run again, rather than reading 2 as a pass.

docs/agents-dev.md goes deeper on all of the above. Written for humans:
README.md, docs/developing.md, SPECIFICATIONS.md.

# Viper outcome cache

Use this note when changing how the test suite, `agent-scripts/test.sh` or the land gate decides whether to run
Silicon on a generated Viper program. It records the design and the measurements behind it. Statements are tagged
**[E]** for evidence checked while writing this note (a command, a source location or a run), and **[H]** for
hypotheses that an implementation stage has to confirm.

## What exists

- `VerificationResultCache` (`formver.compiler-plugin/viper/src/.../VerificationResultCache.kt`) stores successful
  outcomes only. One file per key, written to a temporary file and renamed into place, so concurrent writers race to
  identical content. An entry whose bytes differ from the expected record is deleted, reported and
  re-verified. **[E]**
- `SiliconFrontend.verify` consults it when constructed with a `cacheDirectory`, using the [key inputs](#key).
  Caching refuses to start when the custom Silicon arguments include anything other than numeric timeout and seed
  options. Consistency checking runs on hits too. **[E]**
- The printed program is close to a faithful rendering of the AST: Silver prints a domain function application
  with an explicit result type whenever it has type arguments, and prints `@` annotations. Positions and other
  `Info` nodes are not printed; they do not change whether a program verifies. **[E]**
  (`silver/.../ast/pretty/PrettyPrinter.scala`, `showAnnotation` and the `DomainFuncApp` case.)
- Users enable it with the `verification_cache_directory` plugin option (README.md). The test fixtures enable it
  with `-Pformver.verificationCacheDirectory=<dir>`. `test.sh` passes that property in verification modes.
  `land-gate.yaml` uses `agent-scripts/gate-test.sh` with the [cold sample](#the-land-gate). **[E]**
- `test.sh --verify-changed` runs the `update` task: it verifies a test file only when its conversion output
  differs from the committed golden. It skips per file rather than per program, and it does not notice a change of
  Silicon, Z3 or the encoding that leaves the golden text alone. **[E]**
- Gradle's configuration cache is on (`gradle.properties`). An environment variable that the test task reads at
  configuration time is frozen into the cached configuration and survives later runs with a different value. A
  cache switch has to reach the task as a `-P` property or through a `providers.environmentVariable` input. **[E]**
  (An environment variable set for one run is still in effect in the next run, where it is unset.)

## Measurements

Full `:formver.compiler-plugin:test --rerun`, 16 cores, with other sessions loading the host
(load average 17–23), four declaration jobs, two Silicon verifiers each. Per-program times come from temporary
instrumentation of `SiliconFrontend.verify`. **[E]**

Two source trees are measured. Configuration A has 2772 verified programs per run. Configuration B has 2658; its
encoding code and test files differ from A's, and it lacks some of A's negative tests. Both use the same Silicon,
Z3 and JVM.

| Run | Wall | Programs | Cache hits | Silicon seconds, successes | Silicon seconds, failures |
|---|---|---|---|---|---|
| A, conversion only (`untilConversion`) | 62 s | — | — | — | — |
| A, empty cache | 612 s | 2772 | 84 (repeats within the run) | 1546 | 349 |
| A, cache filled by A | 329 s, 318 s | 2772 | 2234 | 1 | 715, 681 |
| B, cache filled by A | 125 s | 2658 | 2141 of 2145 successes | 1 | 202 |

- A has 2640 distinct programs per run; 2152 verify, 535 fail, one times out. The median program takes 223 ms;
  the ten slowest take 30 % of all Silicon time; the slowest takes 137 s. **[E]**
- With an empty cache, A spends 18 % of its Silicon time on failing programs. With a filled cache those are all
  that is left, and they are re-verified concurrently, so each takes longer: A's slowest failures run 50–63 s with
  the filled cache and 10–51 s with the empty one. **[E]** The failing programs that take longest belong to the
  negative tests that A has and B lacks **[H]** (the gap between the 202 s and 681 s failure columns; host load
  differed between those runs).
- Storage: A's 2125 entries use 8.6 MB on disk, almost all of it file-system blocks for files of 100 bytes. **[E]**

Timings are noisy on this host; the ratios hold between A's two filled-cache runs. Expected savings interprets
these rows.

## Design

### Key

The success key is a length-prefixed SHA-256 over the printed program (`Program.toString()`), the bytes of the
Silicon and Silver artifacts, the effective Silicon arguments (timeouts, `--numberOfErrorsToReport 0`,
`--numberOfParallelVerifiers` and `--counterexample`), plugin options, JVM version, OS and architecture, plus:

1. A digest of the `formver.compiler-plugin/viper` module's class files (the jar, or every class file under
   the class directory when tests run from `build/classes`). This module decides what counts as a success and
   writes the entry, so a work-in-progress change to it cannot write entries that unmodified code then trusts.
2. The Z3 bytes, excluding the `--z3Exe` path from the key. Worktrees and the gate then share entries
   whenever they run the same Z3.
3. A format version string per entry kind, so a change to the record layout starts a disjoint key space.

The Viper prelude and the built-in helper functions are part of the printed program, so they are covered by the
text. Test-mode inputs that change verification (vacuity probes, counterexample mode) change either the text or the
Silicon arguments. **[E]**

### What is stored

- A success: the existing record.
- A failure (in the failure-cache extension): the error list, replayed against the current AST. Each Silver error is a Scala case
  class whose fields are AST nodes, an error reason (itself a case class over nodes), or primitives. The record
  stores, per error, the class names and the fields, with each node as its index in a pre-order traversal of the
  program. Replay rebuilds the objects through their constructors on the current AST's nodes. Because diagnostics
  read positions and `Info` from those nodes, a failure key also covers a pre-order dump of every node's position
  and `Info` rendering, which the printed text omits. A failure is stored only when every error is
  reconstructable, carries no counterexample or failure context, and the program was not a vacuity probe; otherwise
  it is not stored. **[H]** that every error class the suite produces fits this shape; the implementation confirms
  it on the suite before storing failures.
- Never stored: timeouts (`VerificationTimeoutException`), programs Viper rejects
  (`InvalidViperProgramException`), and any exception out of Silicon.

Replay that fails to rebuild an error re-verifies the program and counts it as a replay miss. This is a
best-effort path: re-verification gives the exact uncached result.

### Store

- Location: `$SNAKT_VERIFICATION_CACHE` if set, else `${XDG_CACHE_HOME:-$HOME/.cache}/snakt/verification`. It is
  outside every checkout, so worktrees and the gate checkout (same host user) share it.
- Layout: `<first two hex digits>/<key>.success` or `.failure`, so no directory holds more than a few hundred
  entries.
- Concurrency: write to a temporary file in the shard directory, then atomic rename. Readers see either nothing or
  a complete entry. Two writers of one key write identical bytes.
- Corruption: an entry that fails to parse, or whose embedded key differs from its file name, is deleted and the
  program re-verified. Each such entry is counted and reported in the run summary, so a damaged store is visible
  without failing an unrelated test.
- Eviction: a hit sets the entry's modification time when it is older than a day. `test.sh` deletes entries not
  touched for 30 days, at most once a day (a stamp file in the store). One suite's entries take under 10 MB
  (see Measurements), so the store holds the entries of the trees in use in the tens of megabytes.

### Reporting

Goldens do not change between a hit and a fresh run. The test JVM appends one line per program to a report
directory that `test.sh` passes as `-Pformver.verificationCacheReport=<dir>`: key prefix, `hit`/`miss`/`stored`/
`unstorable`/`corrupt`, and Silicon time on a miss. `test.sh` prints one summary line after the test counts:
`Verification: N programs from cache, M verified (T s of Silicon time), K corrupt entries replaced.` A verify run
with no cache prints `Verification: cache off`.

### Forcing a cold run

- `test.sh --cold` and `SNAKT_VERIFICATION_CACHE=off` run without reading or writing the store.
- `test.sh --refresh-cache` verifies everything, and replaces entries whose fresh outcome is cacheable. A
  disagreement between a stored and a fresh outcome fails the run and names the program, because it means the key
  misses an input or Silicon is not deterministic on that program.

### The land gate

The gate verifies only the tests whose testData the candidate changes
([Landing and nightly](../agents-dev.md#landing-and-nightly)). For those it passes
`-Pformver.verificationCacheDirectory=<store>` and `-Pformver.verificationColdSample=<commit sha>`. With a cold
sample set, every hit whose key, hashed with the sample value, falls into the first 1/20 of the hash range is
verified anyway and compared with the stored outcome. A disagreement fails the gate run with the program and both
outcomes.

A cached outcome can hide a regression only if the key misses an input or Silicon's outcome on a program is not a
function of its inputs. The first is bounded by the tool digests in the key; the second is what the cold sample
catches, and what the nightly run, which verifies every program without the cache, checks in full. A flaky program
that once verified stays verified in the cache until sampled; uncached, it would have failed the gate at random.

`check-gradle-consumer.sh` runs nightly and verifies cold: it checks the published plugin, and the
caller-stays-verified check edits a spec and needs fresh verification.

### `--verify-changed`

With a filled cache, `--verify` re-runs Silicon only on programs whose outcome is not stored (see Expected
savings), and it checks every program against the current tools. `--verify-changed` then has no use that `--verify` does not cover better.

## Expected savings

| Use | With success cache | With failure cache |
|---|---|---|
| Full verify / `--update-goldens`, nothing changed | about half of the cold run | most of the remaining gap to the conversion floor **[H]** |
| Pattern run of one test | small | small |

Developers mostly run the conversion loop, which never calls Silicon; the savings land on `--verify`
and `--update-goldens`.

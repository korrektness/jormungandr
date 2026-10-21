# ArrayDeque source provenance

Upstream: https://github.com/JetBrains/kotlin
Tag: v2.3.0
Commit: f95cb2f7693a15966fd8fd9b1d78d440545e7b66
Source: libraries/stdlib/src/kotlin/collections/ArrayDeque.kt
SHA-256: 1369874f80ec51c6c484276aacc53fe4a401870b1de0e907cc1217278d6c40ae
License: Apache-2.0. LICENSE.txt and NOTICE.txt are copied from license/ at this tag.

## Residual trust

The stripped diff is assumed behaviour-preserving for its listed hunks:

- D-1: superclass removal.
- D-2: size as a @Pure getter over a private var.
- D-3: modCount as a field.
- D-4: private copies of AbstractList helpers.
- D-5: @InlineOnly removal.
- D-6: shared empty sentinel replaced by arrayOfNulls(0), and
  elementData === emptyElementData replaced by elementData.size == 0.
- D-7: removed members.
- D-8: added specification.

The correspondence checker guards against accidental source drift; it is not a
proof of equivalence. Its independent reference-assignment pin compares enclosing
declaration paths, operators, multiplicity and RHS tokens for bare or current-instance
`elementData` assignments and increments. It rejects local/parameter/destructuring
shadows of that name and property initializers. Indexed writes mutate contents and
are covered by the approved diff only.

Residual syntactic trust includes aliasing, other receivers, name resolution
(including labels and the empty-sentinel substitution), lambda execution, control
flow and reachability within a declaration, and the specification-erasure rules.
Approval of a diff does not establish that those changes preserve behaviour.

The proof is of the port, not of the shipped class.
The committed ArrayDeque.port.diff is the deviation list; this file labels its
residual trust. See docs/targets/arraydeque-plan.md, “D2, D-6 and residual trust:
port correspondence”, for the correspondence check and the scope of the proof.

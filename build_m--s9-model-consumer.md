# Silicon recorder teardown

Use this note when inspecting `SiliconFrontend` shutdown.

Silicon's `MultiRunRecorders` is a JVM-global singleton with a mutable recorder map and an ArrayBuffer of PrintWriter sinks. Each backend's `stop` iterates the recorders, closes the sinks, then clears both collections. Overlapping stops can invalidate a sink iterator when another stop clears the ArrayBuffer, producing `ConcurrentModificationException` in `CheckedIndexedSeqView.CheckedIterator`/`MutationTracker`.

The pinned backend has no executable consumers of `MultiRunRecorders.get` or its line/counting recorder APIs. Its verifier workers do not register sinks or mutate nested recorder buffers. Per-verifier proof-state function/heap recorders are distinct from this singleton. The dependency pin is in `gradle/libs.versions.toml`.

The lease policy is owned by [SiliconConfigurationGate's KDoc](formver.compiler-plugin/viper/src/org/jetbrains/kotlin/formver/viper/SiliconConfigurationGate.kt). Timeout cleanup runs outside the verification action to avoid upgrading an active lease. Silicon's worker-pool teardown precedes recorder-sink teardown.

[SiliconConfigurationGateTest](formver.compiler-plugin/test/org/jetbrains/kotlin/formver/viper/SiliconConfigurationGateTest.kt) covers the iterator/clear interleaving and failed lifecycle callbacks with explicit latch ordering.

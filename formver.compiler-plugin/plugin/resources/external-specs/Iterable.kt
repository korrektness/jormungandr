package org.jetbrains.kotlin.formver.external.collections

import org.jetbrains.kotlin.formver.plugin.*

@ExternalSpec("kotlin.collections.Iterable")
internal interface IterableModel<out E> {
    @GhostObserver @Pure @Unique fun iterationSequence(): IterationSequence<E> = ghostObserver()
}

@ExternalSpec("kotlin.collections.Map.Entry")
internal interface EntryModel<out K, out V> {
    @Pure val key: K
    @Pure val value: V
}

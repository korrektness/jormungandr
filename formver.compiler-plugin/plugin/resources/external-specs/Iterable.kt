package org.jetbrains.kotlin.formver.external.collections

import org.jetbrains.kotlin.formver.plugin.*

@ExternalSpec("kotlin.collections.Iterable")
internal interface IterableModel<out E> {
    @GhostObserver @Pure @Unique fun iterationSequence(): IterationSequence<E> = ghostObserver()
}

@ExternalSpec("kotlin.collections.Collection")
internal interface CollectionModel<out E> : IterableModel<E> {
    // https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.collections/-collection/size.html: number of elements.
    @Pure @get:Unique val size: Int
    @Law @Pure @Unique fun nonnegativeSize(): Boolean = size >= 0

    // https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.collections/-collection/is-empty.html: true iff empty.
    @Pure @Unique fun isEmpty(): Boolean
    @SpecOf("isEmpty") @Unique fun collectionEmptySpec() {
        postconditions<Boolean> { result -> result == (size == 0) }
    }
}

@ExternalSpec("kotlin.collections.Map.Entry")
internal interface EntryModel<out K, out V> {
    @Pure val key: K
    @Pure val value: V
}

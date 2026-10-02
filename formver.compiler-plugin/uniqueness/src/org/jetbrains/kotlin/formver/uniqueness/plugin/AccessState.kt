package org.jetbrains.kotlin.formver.uniqueness.plugin

import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol

/** The paths an expression accesses, keyed by path components of type [Key]. */
typealias AccessTrie<Key> = PathTrie<Key, Access>

typealias AccessState = AccessTrie<FirBasedSymbol<*>>

val EmptyAccessState = AccessState(Access.Intermediate)

/**
 * Returns `true` if [this] [AccessState] represents the end of a path (terminal), `false` otherwise.
 */
val AccessTrie<*>.isTerminal: Boolean
    get() = children.isEmpty() || data == Access.Terminal

/**
 * Enumerates all the paths accessed in [this] access-state.
 */
fun <Key> AccessTrie<Key>.enumeratePaths(): Sequence<List<Key>> =
    enumerate { data == Access.Terminal }

/**
 * Performs the join of two [AccessState]s.
 *
 * @param this the [AccessState]
 * @param other the other [AccessState] to join with.
 * @return the [AccessState] containing accesses present in both inputs.
 */
fun <Key> AccessTrie<Key>.join(other: AccessTrie<Key>): AccessTrie<Key> =
    join(other, Access::join)

/**
 * Concatenates every path in [this] with every path in [other].
 *
 * Worked example (`*` marks `Terminal` nodes; the top-row labels are the trie's own root and are not symbols inside the
 * trie):
 *
 * ```
 *   t0:                paths(t0) = { [b], [b, c], [d] }
 *     \__b*
 *     \  \__c*
 *     \__d*
 *
 *   t1:                paths(t1) = { [f], [g] }
 *     \__f*
 *     \__g*
 *
 *   t0.append(t1):     paths = { [b, f], [b, g], [b, c, f], [b, c, g], [d, f], [d, g] }
 *     \__b
 *     \  \__f*
 *     \  \__g*
 *     \  \__c
 *     \     \__f*
 *     \     \__g*
 *     \__d
 *        \__f*
 *        \__g*
 *
 * In this example, every path in `t1` is appended after every terminal path in `t0`. As a result, a node that is
 * [Terminal] in `t0` can become [Intermediate] in `t0.append(t1)`, because it is no longer an endpoint and now has
 * children contributed by `t1`.
 * ```
 */
fun <Key> AccessTrie<Key>.append(other: AccessTrie<Key>): AccessTrie<Key> {
    if (children.isEmpty()) return other

    var newChildren = children

    for ((symbol, child) in children) {
        newChildren = newChildren.put(symbol, child.append(other))
    }

    if (data == Access.Terminal) {
        for ((symbol, otherChild) in other.children) {
            val thisChild = newChildren[symbol]

            newChildren = newChildren.put(
                symbol,
                thisChild?.join(otherChild) ?: otherChild,
            )
        }

        return copy(data = other.data, children = newChildren)
    } else {
        return copy(children = newChildren)
    }
}

/**
 * Alters a uniqueness state at every access position specified by this access state.
 *
 * @param this the [AccessTrie] specifying the access positions to alter.
 * @param uniquenessState the [UniquenessTrie] to alter.
 * @param declaredUniqueness the declared uniqueness of a path component.
 * @param transform the function to apply to each access position.
 *
 * If any of the intermediate path components is not resolved within [uniquenessState], the uniqueness of those
 * components is automatically inferred to be the join between the declared uniqueness of the component and the
 * uniqueness of the parent.
 */
fun <Key> AccessTrie<Key>.transformOnTerminals(
    uniquenessState: UniquenessTrie<Key>,
    declaredUniqueness: (Key) -> Uniqueness,
    transform: (Key, UniquenessTrie<Key>) -> UniquenessTrie<Key>
): UniquenessTrie<Key> {
    var newUniquenessState = uniquenessState

    for ((key, accessChild) in children) {
        val uniquenessChild = uniquenessState.children[key]
            ?: UniquenessTrie(declaredUniqueness(key).join(newUniquenessState.data))

        val newUniquenessChild = accessChild
            .transformOnTerminals(uniquenessChild, declaredUniqueness, transform)

        newUniquenessState = newUniquenessState.putChild(
            key,
            if (accessChild.isTerminal) {
                transform(key, newUniquenessChild)
            } else {
                newUniquenessChild
            }
        )
    }

    return newUniquenessState
}

/**
 * Restores the accessed position specified by this access state in the uniqueness state to its declared uniqueness.
 *
 * @param this the [AccessTrie] specifying the accessed position to initialize.
 * @param uniquenessState the [UniquenessTrie] to initialize the accessed position in.
 * @param declaredUniqueness the declared uniqueness of a path component.
 */
fun <Key> AccessTrie<Key>.initialize(
    uniquenessState: UniquenessTrie<Key>,
    declaredUniqueness: (Key) -> Uniqueness,
): UniquenessTrie<Key> =
    transformOnTerminals(uniquenessState, declaredUniqueness) { key, state ->
        state.copy(data = declaredUniqueness(key))
    }

context(context: CheckerContext)
fun AccessState.initialize(uniquenessState: UniquenessState): UniquenessState =
    initialize(uniquenessState) { it.resolveDeclaredUniqueness() }

/**
 * Moves the accessed position specified by this access state in the uniqueness state.
 *
 * @param this the [AccessTrie] specifying the accessed position to move.
 * @param uniquenessState the [UniquenessTrie] to move the accessed position in.
 * @param declaredUniqueness the declared uniqueness of a path component.
 */
fun <Key> AccessTrie<Key>.move(
    uniquenessState: UniquenessTrie<Key>,
    declaredUniqueness: (Key) -> Uniqueness,
): UniquenessTrie<Key> =
    transformOnTerminals(uniquenessState, declaredUniqueness) { _, state ->
        if (state.data <= Uniqueness.Unknown) {
            state.copy(data = Uniqueness.Moved)
        } else {
            state
        }
    }

context(context: CheckerContext)
fun AccessState.move(uniquenessState: UniquenessState): UniquenessState =
    move(uniquenessState) { it.resolveDeclaredUniqueness() }

/**
 * Joins the uniqueness values at the terminal access paths of this access state.
 *
 * @param this the [AccessTrie] specifying the terminal paths to read.
 * @param uniquenessState the [UniquenessTrie] to read terminal uniqueness values from.
 * @return the join of all terminal uniqueness values, or [Uniqueness.Unique] when no terminal path is present.
 */
fun <Key> AccessTrie<Key>.projectTerminalUniqueness(uniquenessState: UniquenessTrie<Key>): Uniqueness {
    var result = Uniqueness.Unique

    for (path in enumeratePaths()) {
        result = result.join(uniquenessState.find(path)?.data ?: Uniqueness.Unique)
    }

    return result
}

/**
 * Projects the uniqueness substates at the terminal access paths of this access state.
 *
 * @param this the [AccessTrie] specifying the terminal paths to project.
 * @param uniquenessState the [UniquenessTrie] to project terminal substates from.
 * @return a joined [UniquenessTrie] containing the substates found at all terminal paths.
 */
fun <Key> AccessTrie<Key>.projectTerminalUniquenessState(uniquenessState: UniquenessTrie<Key>): UniquenessTrie<Key> {
    val empty = UniquenessTrie<Key>(Uniqueness.Unique)
    var result = empty

    for (path in enumeratePaths()) {
        result = result.join(uniquenessState.find(path) ?: empty)
    }

    return result
}

/**
 * The state after writing [source] to [target], the paths of an assignment or a declaration.
 *
 * When [movesSource] is set, the source moves before the target is written, so that a source below the target
 * (`p = p.next`) is resolved against the old target. A [target] with a single path then holds the source's old
 * substate at its declared uniqueness. A [target] with several possible paths (a conditional receiver) has only one of
 * them written, so each keeps its old substate joined with the one written. A `null` [source] writes nothing, and
 * resets a single target path to its declared uniqueness.
 *
 * [beforeWrite] runs after the source moves and before the target is written. An assignment that calls a setter moves
 * the setter's receivers there.
 */
fun <Key> UniquenessTrie<Key>.assign(
    target: AccessTrie<Key>,
    source: AccessTrie<Key>?,
    movesSource: Boolean,
    declaredUniqueness: (Key) -> Uniqueness,
    beforeWrite: (UniquenessTrie<Key>) -> UniquenessTrie<Key> = { it },
): UniquenessTrie<Key> {
    var newUniquenessState = this

    if (source != null && movesSource) {
        newUniquenessState = source.move(newUniquenessState, declaredUniqueness)
    }
    newUniquenessState = beforeWrite(newUniquenessState)

    val targetPaths = target.enumeratePaths().toList()
    if (source == null || targetPaths.size == 1) {
        if (source != null) {
            newUniquenessState =
                newUniquenessState.insert(targetPaths.single(), source.projectTerminalUniquenessState(this))
        }
        return target.initialize(newUniquenessState, declaredUniqueness)
    }

    val sourceUniquenessState = source.projectTerminalUniquenessState(this)
    for (targetPath in targetPaths) {
        val writtenUniquenessState = sourceUniquenessState.copy(data = declaredUniqueness(targetPath.last()))
        val oldUniquenessState = newUniquenessState.find(targetPath) ?: UniquenessTrie(Uniqueness.Unique)
        newUniquenessState = newUniquenessState.insert(targetPath, oldUniquenessState.join(writtenUniquenessState))
    }
    return newUniquenessState
}

context(context: CheckerContext)
fun UniquenessState.assign(
    target: AccessState,
    source: AccessState?,
    movesSource: Boolean,
    beforeWrite: (UniquenessState) -> UniquenessState = { it },
): UniquenessState =
    assign(target, source, movesSource, { it.resolveDeclaredUniqueness() }, beforeWrite)

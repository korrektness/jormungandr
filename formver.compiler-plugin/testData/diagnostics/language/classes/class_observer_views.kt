// RENDER_PREDICATES
// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

interface ClassObservation {
    @Pure @Unique fun <!VIPER_TEXT!>value<!>(): Int
    @Pure @Unique fun <!VIPER_TEXT!>scaled<!>(factor: Int): Int
    @SpecOf("scaled") @Unique fun scaledSpec(factor: Int) {
        preconditions { -715827882 <= value() && value() <= 715827882 && 0 <= factor && factor <= 3 }
    }
    @Pure @get:Unique val size: Int
}

open <!VIPER_TEXT!>class ObservationBase<!>(var n: Int) : ClassObservation {
    @Pure @Unique final override fun <!VIPER_TEXT!>value<!>(): Int = n
    @Pure @Unique final override fun <!VIPER_TEXT!>scaled<!>(factor: Int): Int = n * factor
    @Pure @get:Unique final override val size: Int <!VIPER_TEXT!>get<!>() = n
}

abstract class ObservationMiddle(n: Int) : ObservationBase(n)
<!VIPER_TEXT!>class ObservationLeaf<!>(n: Int, var extra: Int) : ObservationMiddle(n) {
    @Pure @Unique fun <!VIPER_TEXT!>throughThis<!>(): Int {
        preconditions { -1073741824 <= n && n <= 1073741823 }
        return value() + size
    }
}

fun <!VIPER_TEXT!>updateBase<!>(base: @Unique @Borrowed ObservationBase, delta: Int) {
    preconditions { -715827882L <= base.n.toLong() + delta.toLong() && base.n.toLong() + delta.toLong() <= 715827882L }
    postconditions<Unit> {
        base.value() == old(base.value()) + delta &&
            base.scaled(2) == base.value() * 2 && base.size == base.value()
    }
    base.n += delta
}

fun <!VIPER_TEXT!>borrowClassView<!>(leaf: @Unique @Borrowed ObservationLeaf, delta: Int) {
    preconditions { -715827882L <= leaf.n.toLong() + delta.toLong() && leaf.n.toLong() + delta.toLong() <= 715827882L }
    val before = leaf.value()
    updateBase(leaf, delta)
    verify(leaf.value() == before + delta)
    verify(leaf.scaled(2) == leaf.value() * 2)
    verify(leaf.size == leaf.value())
    leaf.extra = 9
    verify(leaf.extra == 9)
}

fun <!VIPER_TEXT!>permanentClassView<!>(leaf: @Unique ObservationLeaf): @Unique ObservationBase {
    postconditions<ObservationBase> { result -> result.value() == old(leaf.value()) }
    return leaf
}

fun <!VIPER_TEXT!>middleView<!>(leaf: @Unique ObservationLeaf): @Unique ObservationMiddle {
    postconditions<ObservationMiddle> { result -> result.value() == old(leaf.value()) }
    return leaf
}

fun <!VIPER_TEXT!>classToInterface<!>(base: @Unique ObservationBase): @Unique ClassObservation {
    postconditions<ClassObservation> { result -> result.value() == old(base.value()) }
    return base
}

<!NOTHING_TO_INLINE!>inline<!> fun <!VIPER_TEXT!>inlineClassView<!>(base: @Unique @Borrowed ObservationBase, delta: Int) {
    preconditions { -715827882L <= base.n.toLong() + delta.toLong() && base.n.toLong() + delta.toLong() <= 715827882L }
    updateBase(base, delta)
}

fun <!VIPER_TEXT!>inlineClassBorrow<!>(leaf: @Unique @Borrowed ObservationLeaf) {
    preconditions { -715827885 <= leaf.n && leaf.n <= 715827879 }
    val before = leaf.value()
    inlineClassView(leaf, 3)
    verify(leaf.value() == before + 3)
    leaf.extra = 7
    verify(leaf.extra == 7)
}

<!VIPER_TEXT!>class ObservationHolder<!>(val leaf: @Unique ObservationLeaf)

fun <!VIPER_TEXT!>classFieldBorrow<!>(holder: @Unique @Borrowed ObservationHolder) {
    preconditions { -715827886 <= holder.leaf.n && holder.leaf.n <= 715827878 }
    val before = holder.leaf.value()
    inlineClassView(holder.leaf, 4)
    verify(holder.leaf.value() == before + 4)
    holder.leaf.extra = 8
    verify(holder.leaf.extra == 8)
}

fun <!VIPER_TEXT!>nullableClassBorrow<!>(leaf: @Unique @Borrowed ObservationLeaf?) {
    preconditions { leaf == null || (-715827884 <= leaf.n && leaf.n <= 715827880) }
    if (leaf != null) {
        val before = leaf.value()
        updateBase(leaf, 2)
        verify(leaf.value() == before + 2)
        leaf.extra = 6
        verify(leaf.extra == 6)
    }
}

@Pure fun <!VIPER_TEXT!>pureInheritedObservation<!>(leaf: @Unique ObservationLeaf): Int {
    preconditions { -1073741824 <= leaf.n && leaf.n <= 1073741823 }
    return leaf.value() + leaf.size
}

fun <!VIPER_TEXT!>wrongClassObservation<!>(leaf: @Unique @Borrowed ObservationLeaf) {
    preconditions { -715827884 <= leaf.n && leaf.n <= 715827880 }
    val before = leaf.value()
    updateBase(leaf, 2)
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>leaf.value() == before + 3<!>)
}

fun <!VIPER_TEXT!>freshClassBorrow<!>() {
    updateBase(ObservationLeaf(1, 0), 2)
}

fun <!VIPER_TEXT!>freshClassView<!>(): @Unique ObservationBase = ObservationLeaf(1, 0)

abstract class AbstractObservation : ClassObservation

<!VIPER_TEXT!>class DirectObservation<!>(var n: Int) : AbstractObservation() {
    @Pure @Unique override fun <!VIPER_TEXT!>value<!>(): Int = n
    @Pure @Unique override fun <!VIPER_TEXT!>scaled<!>(factor: Int): Int = n * factor
    @Pure @get:Unique override val size: Int <!VIPER_TEXT!>get<!>() = n
}

fun <!VIPER_TEXT!>abstractValue<!>(base: @Unique @Borrowed AbstractObservation): Int {
    postconditions<Int> { result -> result == old(base.value()) && base.value() == old(base.value()) }
    return base.value()
}

fun <!VIPER_TEXT!>abstractBorrow<!>(leaf: @Unique @Borrowed DirectObservation) {
    val before = leaf.value()
    val result = abstractValue(leaf)
    verify(result == before)
    verify(leaf.value() == before)
}

fun <!VIPER_TEXT!>abstractToInterface<!>(base: @Unique AbstractObservation): @Unique ClassObservation {
    postconditions<ClassObservation> { result -> result.value() == old(base.value()) }
    return base
}

open <!VIPER_TEXT!>class RefinementBase<!>(var state: Int) : ClassObservation {
    @Pure @Unique final override fun <!VIPER_TEXT!>value<!>(): Int = state
    @Pure @Unique final override fun <!VIPER_TEXT!>scaled<!>(factor: Int): Int = state * factor
    @Pure @get:Unique final override val size: Int <!VIPER_TEXT!>get<!>() = state

    @Unique @Borrowed open fun <!VIPER_TEXT!>advance<!>() {
        preconditions { state < 2147483647 }
        postconditions<Unit> { value() == old(value()) + 1 }
        state++
    }
}

<!VIPER_TEXT!>class RefinementLeaf<!>(state: Int) : RefinementBase(state) {
    @Unique @Borrowed override fun <!VIPER_TEXT!>advance<!>() { state++ }
}

fun <!VIPER_TEXT!>refinementClassClient<!>(leaf: @Unique @Borrowed RefinementLeaf) {
    preconditions { leaf.state < 2147483647 }
    val before = leaf.value()
    leaf.advance()
    verify(leaf.value() == before + 1)
}

interface SharedObservation {
    @Pure fun <!VIPER_TEXT!>constant<!>(): Int
}

open <!VIPER_TEXT!>class SharedObservationBase<!> : SharedObservation {
    @Pure final override fun <!VIPER_TEXT!>constant<!>(): Int = 4
}

<!VIPER_TEXT!>class SharedObservationLeaf<!> : SharedObservationBase()

fun <!VIPER_TEXT!>sharedInheritedObservation<!>(leaf: SharedObservationLeaf) {
    verify(leaf.constant() == 4)
}

fun <!VIPER_TEXT!>baseFieldAccess<!>(base: @Unique @Borrowed ObservationBase) {
    preconditions { -715827882 <= base.n && base.n <= 715827882 }
    verify(base.value() == base.n)
    verify(base.scaled(3) == base.n * 3)
    verify(base.size == base.n)
}

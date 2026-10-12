// FULL_JDK
// RENDER_PREDICATES

import org.jetbrains.kotlin.formver.plugin.*

open <!VIPER_TEXT!>class TransitiveRoot<!>(var n: Int) {
    init { require(n >= 0) }
    @Law @Pure @Unique fun nonnegative(): Boolean = n >= 0
    @Unique @Borrowed fun <!VIPER_TEXT!>increment<!>() { n++ }
}
open <!VIPER_TEXT!>class LawFreeMiddle<!>(n: Int) : TransitiveRoot(n)
<!VIPER_TEXT!>class TransitiveLeaf<!>(n: Int) : LawFreeMiddle(n)

fun <!VIPER_TEXT!>validTransitiveConstruction<!>() {
    val leaf: @Unique TransitiveLeaf = TransitiveLeaf(1)
    leaf.increment()
    verify(leaf.n >= 0)
}
<!VIPER_VERIFICATION_ERROR!>fun <!VIPER_TEXT!>invalidTransitiveMutation<!>(leaf: @Unique @Borrowed TransitiveLeaf) {
    leaf.n = -1
}<!>

<!VIPER_VERIFICATION_ERROR!>open <!VIPER_TEXT!>class InvalidConstructionRoot<!>(var n: Int) {
    init { n = -1 }
    @Law @Pure @Unique fun nonnegative(): Boolean = n >= 0
}<!>
<!VIPER_VERIFICATION_ERROR!>open <!VIPER_TEXT!>class InvalidRootMiddle<!>(n: Int) : InvalidConstructionRoot(n)<!>
<!VIPER_VERIFICATION_ERROR!><!VIPER_TEXT!>class InvalidRootLeaf<!>(n: Int) : InvalidRootMiddle(n)<!>

<!VIPER_VERIFICATION_ERROR!>open <!VIPER_TEXT!>class InitializingMiddle<!>(n: Int) : TransitiveRoot(n) {
    init { this.n = -1 }
}<!>
<!VIPER_VERIFICATION_ERROR!><!VIPER_TEXT!>class InitializingLeaf<!>(n: Int) : InitializingMiddle(n)<!>

open <!VIPER_TEXT!>class SecondaryMiddle<!>(n: Int) : TransitiveRoot(n) {
    constructor() : this(-1)
}
<!VIPER_TEXT!>class SecondaryLeaf<!>(n: Int) : SecondaryMiddle(n)

fun <!VIPER_TEXT!>effectfulInitializer<!>(n: Int): Int = n
open <!VIPER_TEXT!>class ImpureInitializerMiddle<!>(n: Int) : TransitiveRoot(n) {
    val extra: Int = effectfulInitializer(n)
}
<!VIPER_TEXT!>class ImpureInitializerLeaf<!>(n: Int) : ImpureInitializerMiddle(n)

open <!VIPER_TEXT!>class InheritedBodyRoot<!>(var n: Int) {
    init { require(n >= 0) }
    @Law @Pure @Unique fun nonnegative(): Boolean = n >= 0
    @NeverVerify @Unique @Borrowed fun <!VIPER_TEXT!>breakState<!>() { n = -1 }
}
<!VIPER_VERIFICATION_ERROR!>open <!VIPER_TEXT!>class InheritedBodyMiddle<!>(n: Int) : InheritedBodyRoot(n)<!>
<!VIPER_VERIFICATION_ERROR!><!VIPER_TEXT!>class InheritedBodyLeaf<!>(n: Int) : InheritedBodyMiddle(n)<!>

open <!VIPER_TEXT!>class DefaultArgumentRoot<!>(x: Int, y: Int = x + 1, offset: Int = y + 2) {
    val z: Int = y
    val w: Int = offset
    init { require(y >= 0 && offset == y + 2) }
    @Law @Pure fun validFields(): Boolean = z >= 0 && w == z + 2
}
<!VIPER_TEXT!>class RenamedArgumentLeaf<!>(n: Int) : DefaultArgumentRoot(n * 2)
<!VIPER_TEXT!>class SameNameArgumentLeaf<!>(x: Int) : DefaultArgumentRoot(x * 2)
<!VIPER_TEXT!>class ExplicitDefaultLeaf<!>(x: Int) : DefaultArgumentRoot(x * 2, y = x + 10)

fun <!VIPER_TEXT!>validSuperclassDefaults<!>() {
    val renamed: @Unique RenamedArgumentLeaf = RenamedArgumentLeaf(1)
    verify(renamed.z == 3)
    verify(renamed.w == 5)
    val sameName: @Unique SameNameArgumentLeaf = SameNameArgumentLeaf(1)
    verify(sameName.z == 3)
    verify(sameName.w == 5)
    val explicit: @Unique ExplicitDefaultLeaf = ExplicitDefaultLeaf(-1)
    verify(explicit.z == 9)
    verify(explicit.w == 11)
}

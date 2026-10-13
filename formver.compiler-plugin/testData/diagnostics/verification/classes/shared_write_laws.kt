// FULL_JDK
// RENDER_PREDICATES

import org.jetbrains.kotlin.formver.plugin.*

<!VIPER_TEXT!>class SharedLaw<!>(var value: Int) {
    init { require(value > 0) }
    @Law @Pure @Unique fun invariant(): Boolean = value > 0
    @Borrowed fun <!VERIFICATION_SKIPPED!>clear<!>() { <!UNSUPPORTED_FEATURE!>value = 0<!> }

    @Unique @Borrowed fun <!VIPER_TEXT!>preserve<!>() { value = 1 }
}

fun <!VIPER_TEXT!>sharedLawClient<!>(c: @Unique SharedLaw) {
    c.clear()
    verify(c.value > 0)
    refute(false)
}

<!VIPER_TEXT!>class ChildLaw<!>(var value: Int) {
    init { require(value > 0) }
    @Pure @Unique fun <!VIPER_TEXT!>observed<!>(): Int = value
    @Pure @Unique fun <!VIPER_TEXT!>indirect<!>(): Int = observed()
    @Law @Pure @Unique fun invariant(): Boolean = indirect() > 0
}

<!VIPER_TEXT!>class Parent<!>(val child: @Unique ChildLaw) {
    @Borrowed fun <!VERIFICATION_SKIPPED!>clearChild<!>() { <!UNSUPPORTED_FEATURE!>child.value = 0<!> }
}

fun <!VIPER_TEXT!>nestedLawClient<!>(p: @Unique Parent) {
    p.clearChild()
    verify(p.child.value > 0)
    refute(false)
}

open <!VIPER_TEXT!>class UnprotectedBase<!>(var value: Int) {
    @Borrowed fun <!VIPER_TEXT!>clear<!>() { value = 0 }
}

<!VIPER_TEXT!>class UnprotectedDerived<!>(value: Int) : UnprotectedBase(value) {
    init { require(value > 0) }
    @Pure @Unique fun <!VIPER_TEXT!>observed<!>(): Int = value
    @Law @Pure @Unique fun invariant(): Boolean = <!INVALID_LAW!>value<!> > 0
    @Law @Pure @Unique fun indirectInvariant(): Boolean = <!INVALID_LAW!>observed()<!> > 0
}

open <!VIPER_TEXT!>class ProtectedBase<!>(@LawBound var value: Int) {
    @Borrowed fun <!VERIFICATION_SKIPPED, VERIFICATION_SKIPPED!>clear<!>() { <!UNSUPPORTED_FEATURE, UNSUPPORTED_FEATURE!>value = 0<!> }
}

class ProtectedDerived<!VERIFICATION_SKIPPED!>(value: Int)<!> : ProtectedBase(value) {
    init { require(value > 0) }
    @Law @Pure @Unique fun invariant(): Boolean = value > 0
}

open <!VIPER_TEXT!>class ImplicitBase<!>(var value: Int) {
    init { require(value > 0) }
    @Pure @Unique fun <!VIPER_TEXT!>observed<!>(): Int = value
    @Law @Pure @Unique fun invariant(): Boolean = observed() > 0
    @Borrowed fun <!VERIFICATION_SKIPPED, VERIFICATION_SKIPPED!>clear<!>() { <!UNSUPPORTED_FEATURE, UNSUPPORTED_FEATURE!>value = 0<!> }
}

class ImplicitDerived<!VERIFICATION_SKIPPED!>(value: Int)<!> : ImplicitBase(value) {
    @Law @Pure @Unique fun additionalLaw(): Boolean = observed() > 0
}

open <!VIPER_TEXT!>class AnnotatedBase<!>(@LawBound var value: Int)
<!VIPER_TEXT!>class AnnotatedDerived<!>(value: Int) : AnnotatedBase(value) {
    init { require(value > 0) }
    @Law @Pure @Unique fun invariant(): Boolean = value > 0
}
fun <!VIPER_TEXT!>annotatedClient<!>(c: @Unique AnnotatedDerived) {
    c.value = 1
    verify(c.value > 0)
    refute(false)
}

open <!VIPER_TEXT!>class UnprotectedPropertyBase<!>(var value: Int) {
    @get:Unique @Pure val observation: Int <!VIPER_TEXT!>get<!>() = value
}
<!VIPER_TEXT!>class UnprotectedPropertyDerived<!>(value: Int) : UnprotectedPropertyBase(value) {
    init { require(value > 0) }
    @Law @Pure fun invariant(): Boolean = <!INVALID_LAW!>observation<!> > 0
}

open <!VIPER_TEXT!>class ProtectedPropertyBase<!>(@LawBound var value: Int) {
    @get:Unique @Pure val observation: Int <!VIPER_TEXT!>get<!>() = value
}
<!VIPER_TEXT!>class ProtectedPropertyDerived<!>(value: Int) : ProtectedPropertyBase(value) {
    init { require(value > 0) }
    @Law @Pure fun invariant(): Boolean = observation > 0
}
fun <!VERIFICATION_SKIPPED!>inheritedPropertySharedWrite<!>(c: ProtectedPropertyBase) { <!UNSUPPORTED_FEATURE!>c.value = 0<!> }
fun <!VIPER_TEXT!>inheritedPropertyClient<!>(c: @Unique ProtectedPropertyDerived) {
    verify(c.value > 0)
    refute(false)
}

open <!VIPER_TEXT!>class StableBase<!>(val stable: Int)
<!VIPER_TEXT!>class StableDerived<!>(stable: Int) : StableBase(stable) {
    init { require(stable > 0) }
    @Law @Pure fun invariant(): Boolean = stable > 0
}

fun <!VIPER_TEXT!>lawFreeOpenClient<!>(c: @Unique UnprotectedBase) {
    c.clear()
    c.value = 2
    verify(c.value == 2)
    refute(false)
}

fun <!VIPER_TEXT!>preservingClient<!>(c: @Unique SharedLaw) {
    c.preserve()
    verify(c.value > 0)
    refute(false)
}

fun <!VERIFICATION_SKIPPED!>externalSharedWrite<!>(c: SharedLaw) { <!UNSUPPORTED_FEATURE!>c.value = 0<!> }
fun <!VERIFICATION_SKIPPED!>externalInheritedWrite<!>(c: ProtectedBase) { <!UNSUPPORTED_FEATURE!>c.value = 0<!> }
fun <!VERIFICATION_SKIPPED!>externalNestedWrite<!>(p: Parent) { <!UNSUPPORTED_FEATURE!>p.child.value = 0<!> }

<!VIPER_TEXT!>class PartlyBound<!>(var bound: Int, var free: Int) {
    init { require(bound > 0) }
    @Law @Pure @Unique fun invariant(): Boolean = bound > 0
    @Borrowed fun <!VIPER_TEXT!>clearFree<!>() { free = 0 }
}

<!VIPER_TEXT!>class PropertyLaw<!>(var value: Int) {
    init { require(value > 0) }
    @get:Unique @Pure val observation: Int <!VIPER_TEXT!>get<!>() = value
    @Law @Pure fun invariant(): Boolean = observation > 0
    @Borrowed fun <!VERIFICATION_SKIPPED!>clear<!>() { <!UNSUPPORTED_FEATURE!>value = 0<!> }
}
fun <!VERIFICATION_SKIPPED!>incrementShared<!>(c: SharedLaw) { <!UNSUPPORTED_FEATURE!>c.value++<!> }
fun <!VERIFICATION_SKIPPED!>subtypeSharedWrite<!>(c: AnnotatedDerived) { <!UNSUPPORTED_FEATURE!>c.value = 0<!> }
fun <!VIPER_TEXT!>partlyBoundClient<!>(c: @Unique PartlyBound) {
    c.clearFree()
    verify(c.bound > 0)
    refute(false)
}

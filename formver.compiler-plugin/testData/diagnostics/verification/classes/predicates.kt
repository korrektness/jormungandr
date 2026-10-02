// RENDER_PREDICATES

import org.jetbrains.kotlin.formver.plugin.Unique
import org.jetbrains.kotlin.formver.plugin.Borrowed
import org.jetbrains.kotlin.formver.plugin.AlwaysVerify

open class Baz()

class PrimitiveFields(val a: Int, var b: Int)

class ReferenceField(val pf: PrimitiveFields) : Baz()

class Recursive(val next: Recursive?)

@AlwaysVerify
fun <!VIPER_TEXT!>useClasses<!>(rf: ReferenceField, rec: Recursive) { }

open class A() {
    val x: Int = 1
    var y: Int = 2
}
open class B() : A()
class C() : B()

@AlwaysVerify
fun <!VIPER_TEXT!>threeLayersHierarchy<!>(c: C) { }

@AlwaysVerify
fun <!VIPER_TEXT!>listHierarchy<!>(xs: MutableList<Int>) { }

class T()

open class S()

class Foo(val w: Int, var x: Int, val y: @Unique T, var z: @Unique T) : S()

@AlwaysVerify
fun <!VIPER_TEXT!>unique_foo_arg<!>(foo: @Unique Foo) {}

@AlwaysVerify
fun <!VIPER_TEXT!>nullable_unique_arg<!>(t: @Unique T?) {}

@AlwaysVerify
fun <!VIPER_TEXT!>borrowed_unique_arg<!>(t: @Unique @Borrowed T) {}

@AlwaysVerify
fun @Unique T.<!VIPER_TEXT!>unique_receiver<!>() {}

@AlwaysVerify
fun @Unique @Borrowed T.<!VIPER_TEXT!>borrowed_unique_receiver<!>() {}

@AlwaysVerify
fun <!VIPER_TEXT!>unique_result<!>(): @Unique T { return T() }

@AlwaysVerify
fun <!VIPER_TEXT!>unique_nullable_result<!>(): @Unique T? { return null }

// RENDER_PREDICATES

import org.jetbrains.kotlin.formver.plugin.Unique
import org.jetbrains.kotlin.formver.plugin.Borrowed

open <!VIPER_TEXT!>class Baz<!>()

<!VIPER_TEXT!>class PrimitiveFields<!>(val a: Int, var b: Int)

<!VIPER_TEXT!>class ReferenceField<!>(val pf: PrimitiveFields) : Baz()

<!VIPER_TEXT!>class Recursive<!>(val next: Recursive?)

fun <!VIPER_TEXT!>useClasses<!>(rf: ReferenceField, rec: Recursive) { }

open <!VIPER_TEXT!>class A<!>() {
    val x: Int = 1
    var y: Int = 2
}
open <!VIPER_TEXT!>class B<!>() : A()
<!VIPER_TEXT!>class C<!>() : B()

fun <!VIPER_TEXT!>threeLayersHierarchy<!>(c: C) { }

fun <!VIPER_TEXT!>listHierarchy<!>(xs: MutableList<Int>) { }

<!VIPER_TEXT!>class T<!>()

open <!VIPER_TEXT!>class S<!>()

<!VIPER_TEXT!>class Foo<!>(val w: Int, var x: Int, val y: @Unique T, var z: @Unique T) : S()

fun <!VIPER_TEXT!>unique_foo_arg<!>(foo: @Unique Foo) {}

fun <!VIPER_TEXT!>nullable_unique_arg<!>(t: @Unique T?) {}

fun <!VIPER_TEXT!>borrowed_unique_arg<!>(t: @Unique @Borrowed T) {}

fun @Unique T.<!VIPER_TEXT!>unique_receiver<!>() {}

fun @Unique @Borrowed T.<!VIPER_TEXT!>borrowed_unique_receiver<!>() {}

fun <!VIPER_TEXT!>unique_result<!>(): @Unique T { return T() }

fun <!VIPER_TEXT!>unique_nullable_result<!>(): @Unique T? { return null }

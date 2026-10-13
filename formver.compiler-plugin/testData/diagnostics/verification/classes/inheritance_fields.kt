

open <!VIPER_TEXT!>class FieldA<!>
open <!VIPER_TEXT!>class FieldB<!> : FieldA()
<!VIPER_TEXT!>class C<!>

open <!VIPER_TEXT!>class A<!>(val fieldNotOverride: C) {
    open val fieldOverride: FieldA = FieldA()
}

open <!VIPER_TEXT!>class B<!>(override val fieldOverride: FieldB) : A(C())

fun <!VIPER_TEXT!>createB<!>() {
    val fieldB = FieldB()
    val b = B(fieldB)
    val fieldOverride = b.fieldOverride //should verify that fieldOverride == fieldB
    val fieldNotOverride = b.fieldNotOverride
}

open <!VIPER_TEXT!>class FirstBackingFieldClass<!> {
    open val x: Int = 1
}

open <!VIPER_TEXT!>class NoBackingFieldClass<!>: FirstBackingFieldClass() {
    override val x: Int
        <!VIPER_TEXT!>get<!>() = 1
}

<!VIPER_TEXT!>class SecondBackingFieldClass<!>(override val x: Int) : NoBackingFieldClass()

// BF stands for backing field
fun <!VIPER_TEXT!>createBFsAndNoBF<!>() {
    val fbf = FirstBackingFieldClass()
    val fbfx = fbf.x
    val nbf = NoBackingFieldClass()
    val nbfx = nbf.x
    val sbf = SecondBackingFieldClass(10)
    val sbfx = sbf.x
}

// checks that we don't consider value parameter `a` of `Y`'s constructor a field
open <!VIPER_TEXT!>class X<!>(val a: Int)
<!VIPER_TEXT!>class Y<!>(a: Int) : X(0)

fun <!VIPER_TEXT!>createY<!>() {
    val y = Y(10)
    val ya = y.a
}

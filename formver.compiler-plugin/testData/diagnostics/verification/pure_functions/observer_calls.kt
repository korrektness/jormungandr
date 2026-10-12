// FULL_JDK
import org.jetbrains.kotlin.formver.plugin.*

class Cell(var value: Int)

interface Measure {
    @Pure
    fun <!VIPER_TEXT!>scaled<!>(factor: Int): Int

    @Pure
    fun <!VIPER_TEXT!>read<!>(cell: @Unique Cell): Int

    @Pure
    @Unique
    fun <!VIPER_TEXT!>amount<!>(): Int
}

class Fixed(var value: Int) : Measure {
    @Pure
    override fun <!VIPER_TEXT!>scaled<!>(factor: Int): Int = factor * 2

    @Pure
    override fun <!VIPER_TEXT!>read<!>(cell: @Unique Cell): Int = cell.value

    @Pure
    @Unique
    override fun <!VIPER_TEXT!>amount<!>(): Int = value
}

fun <!VIPER_TEXT!>interfaceCall<!>(m: @Unique @Borrowed Measure, cell: @Unique @Borrowed Cell): Int {
    postconditions<Int> { r -> r == m.scaled(3) + m.read(cell) }
    return m.scaled(3) + m.read(cell)
}

fun <!VIPER_TEXT!>classCall<!>(m: Fixed, cell: @Unique @Borrowed Cell): Int {
    postconditions<Int> { r -> r == 6 + cell.value }
    return m.scaled(3) + m.read(cell)
}

fun <!VIPER_TEXT!>bothCallShapes<!>(m: Fixed, i: @Unique @Borrowed Measure): Int {
    postconditions<Int> { r -> r == 6 + i.scaled(3) }
    return m.scaled(3) + i.scaled(3)
}

fun <!VIPER_TEXT!>ownedInterfaceCall<!>(m: @Unique @Borrowed Measure): Int {
    postconditions<Int> { r -> r == m.amount() }
    return m.amount()
}

fun <!VIPER_TEXT!>ownedClassCall<!>(m: @Unique @Borrowed Fixed): Int {
    postconditions<Int> { r -> r == m.value }
    return m.amount()
}

fun <!VIPER_TEXT!>unboundInterfaceCall<!>(m: Fixed): Int {
    postconditions<Int> { r -> r == 6 }
    val abstract: Measure = m
    return <!OWNERSHIP_NOT_ESTABLISHED!>abstract.scaled(3)<!>
}

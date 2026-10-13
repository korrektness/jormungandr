// FULL_JDK
// RENDER_PREDICATES
import org.jetbrains.kotlin.formver.plugin.*

interface ObservedList {
    @Pure @Unique
    fun <!VIPER_TEXT!>size<!>(): Int

    @Pure @Unique
    fun <!VIPER_TEXT!>get<!>(index: Int): Int

    @SpecOf("add") @Unique @Borrowed
    fun addSpec(value: Int) {
        preconditions { size() < 2147483647 }
        postconditions<Unit> {
            size() == old(size()) + 1
            get(old(size())) == value
        }
    }

    @Unique @Borrowed
    fun <!VIPER_TEXT!>add<!>(value: Int)

    @SpecOf("set") @Unique @Borrowed
    fun setSpec(index: Int, value: Int) {
        preconditions { index >= 0; index < size() }
        postconditions<Unit> {
            size() == old(size())
            get(index) == value
        }
    }

    @Unique @Borrowed
    fun <!VIPER_TEXT!>set<!>(index: Int, value: Int)

    @SpecOf("addFrom") @Unique @Borrowed
    fun addFromSpec(source: @Unique @Borrowed ObservedList) {
        preconditions { source.size() > 0 && size() < 2147483647 }
        postconditions<Unit> {
            size() == old(size()) + 1
            get(old(size())) == old(source.get(0))
            source.size() == old(source.size())
            source.get(0) == old(source.get(0))
        }
    }

    @Unique @Borrowed
    fun <!VIPER_TEXT!>addFrom<!>(source: @Unique @Borrowed ObservedList)
}

<!VIPER_TEXT!>class ObservedCell<!>(var count: Int, var item: Int) : ObservedList {
    @Unique @Borrowed
    override fun <!VIPER_TEXT!>addFrom<!>(source: @Unique @Borrowed ObservedList) {
        item = source.get(0)
        count++
    }

    @Pure @Unique
    override fun <!VIPER_TEXT!>size<!>(): Int = count

    @Pure @Unique
    override fun <!VIPER_TEXT!>get<!>(index: Int): Int = item

    @Unique @Borrowed
    override fun <!VIPER_TEXT!>add<!>(value: Int) {
        preconditions { count < 2147483647 }
        postconditions<Unit> {
            count == old(count) + 1
            item == value
            size() == count
            get(old(size())) == item
        }
        count++
        item = value
    }

    @Unique @Borrowed
    override fun <!VIPER_TEXT!>set<!>(index: Int, value: Int) {
        item = value
    }
}

<!VIPER_TEXT!>class BrokenObservedCell<!>(var count: Int, var item: Int) : ObservedList {
    @Unique @Borrowed
    override fun <!VIPER_TEXT!>addFrom<!>(source: @Unique @Borrowed ObservedList) {
        item = source.get(0)
        count++
    }

    @Pure @Unique
    override fun <!VIPER_TEXT!>size<!>(): Int = count

    @Pure @Unique
    override fun <!VIPER_TEXT!>get<!>(index: Int): Int = item

    <!VERIFICATION_CONDITION_NOT_ESTABLISHED!>@Unique @Borrowed
    override fun <!VIPER_TEXT!>add<!>(value: Int) {
        item = value
    }<!>

    @Unique @Borrowed
    override fun <!VIPER_TEXT!>set<!>(index: Int, value: Int) {
        item = value
    }
}

fun <!VIPER_TEXT!>chainedObserverCalls<!>(list: @Unique @Borrowed ObservedList) {
    preconditions { 0 <= list.size() && list.size() < 2147483647 }
    val index = list.size()
    list.add(7)
    verify(list.size() == index + 1)
    verify(list.get(index) == 7)
    list.set(index, 9)
    verify(list.size() == index + 1)
    verify(list.get(index) == 9)
    refute(false)
}

fun <!VIPER_TEXT!>observerParameterContract<!>(list: @Unique @Borrowed ObservedList) {
    preconditions { 0 <= list.size() && list.size() < 2147483647 }
    postconditions<Unit> {
        list.size() == old(list.size()) + 1
        list.get(old(list.size())) == 5
    }
    list.add(5)
}

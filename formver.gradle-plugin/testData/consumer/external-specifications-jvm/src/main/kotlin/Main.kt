import org.jetbrains.kotlin.formver.plugin.*
import stand.lib.*
import kotlin.math.abs

fun answer(x: Int): Int {
    preconditions { x < 2147483647 }
    return x + 1
}

fun counting(counter: @Unique @Borrowed Counter) {
    verify(counter.value() >= 0)
    val before = counter.value()
    if (before <= 100) {
        val result = counter.advance(3)
        verify(result == before + 3)
        verify(counter.value() == result)
    }
}

fun creating() {
    val counter = create(2)
    verify(counter.value() == 2)
    val result = counter.advance(3)
    verify(result == 5)
    verify(counter.value() == 5)
}

fun doubling() {
    verify(twice(4) == 8)
}

fun absolute() {
    verify(abs(-3) == 3)
}

class Element(val number: Int)

fun stdlibModels(element: Element) {
    val singleton = listOf(7)
    verify(singleton.size == 1 && singleton[0] == 7 && singleton.indexOf(7) == 0)
    val list = mutableListOf<Element>()
    list.add(element)
    verify(list[0] == element && list[0].number == element.number)
    val set = mutableSetOf<Int>()
    set.add(4)
    val duplicate = set.add(4)
    verify(!duplicate && set.size == 1)
    val map = mutableMapOf<Int, Element>()
    map.put(2, element)
    verify(map.containsKey(2) && map[2] == element)
    val text = "hello".substring(1, 4)
    verify(text.length == 3 && text[0] == 'e')
}

fun enqueueOnce(vertex: Int) {
    val queue: @Unique ArrayDeque<Int> = ArrayDeque<Int>()
    verify(queue.size == 0)
    if (!queue.contains(vertex)) {
        queue.addLast(vertex)
        val last = queue.last()
        verify(last == vertex)
    }
}

fun halving(box: @Unique @Borrowed stand.vlib.Box) {
    verify(stand.vlib.half(8) <= 8 && box.size == 1)
}

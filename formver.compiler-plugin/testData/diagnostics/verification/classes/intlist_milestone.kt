// FULL_JDK
// RENDER_PREDICATES
import org.jetbrains.kotlin.formver.plugin.*

interface IntList {
    @Pure @Unique fun <!VIPER_TEXT!>size<!>(): Int
    @Pure @Unique fun <!VIPER_TEXT!>get<!>(i: Int): Int
    @SpecOf("get") @Unique
    fun getSpec(i: Int) {
        preconditions { 0 <= i && i < size() }
    }
    @Law @Pure @Unique fun nonnegative(): Boolean = size() >= 0

    @SpecOf("add") @Unique @Borrowed
    fun addSpec(value: Int) {
        preconditions { 0 <= size() && size() < 2147483647 }
        postconditions<Unit> {
            size() == old(size()) + 1
            get(old(size())) == value
            forAll<Int> { k -> (0 <= k && k < old(size())) implies (get(k) == old(get(k))) }
        }
    }
    @Unique @Borrowed fun <!VIPER_TEXT!>add<!>(value: Int)

    @SpecOf("set") @Unique @Borrowed
    fun setSpec(i: Int, value: Int) {
        preconditions { 0 <= i && i < size() }
        postconditions<Unit> {
            size() == old(size())
            get(i) == value
            forAll<Int> { k -> (0 <= k && k < size() && k != i) implies (get(k) == old(get(k))) }
        }
    }
    @Unique @Borrowed fun <!VIPER_TEXT!>set<!>(i: Int, value: Int)
}

fun <!VIPER_TEXT!>get<!>(list: @Unique @Borrowed IntList, i: Int): Int {
    preconditions { 0 <= i && i < list.size() }
    postconditions<Int> { result ->
        result == old(list.get(i))
        list.size() == old(list.size())
        forAll<Int> { k -> (0 <= k && k < list.size()) implies (list.get(k) == old(list.get(k))) }
    }
    return list.get(i)
}

<!VIPER_TEXT!>class ArrayIntList<!>(var data: @Unique IntArray, var count: Int) : IntList {
    init { require(0 <= count && count <= data.size) }
    @Law @Pure @Unique fun representation(): Boolean = 0 <= count && count <= data.size
    @Pure @Unique override fun <!VIPER_TEXT!>size<!>(): Int = count
    @SpecOf("get") @Unique
    fun concreteGetSpec(i: Int) {
        preconditions { 0 <= i && i < count && i < data.size }
    }
    @Pure @Unique override fun <!VIPER_TEXT!>get<!>(i: Int): Int = data[i]

    @Unique @Borrowed override fun <!VIPER_TEXT!>add<!>(value: Int) {
        val n = count
        val replacement: @Unique IntArray = IntArray(n + 1)
        var i = 0
        while (i < n) {
            loopInvariants {
                0 <= i && i <= n
                count == n
                forAll<Int> { k -> (0 <= k && k < i) implies (replacement[k] == data[k]) }
                forAll<Int> { k -> (0 <= k && k < n) implies (data[k] == old(data[k])) }
            }
            replacement[i] = data[i]
            i++
        }
        replacement[n] = value
        data = replacement
        count = n + 1
    }

    @Unique @Borrowed override fun <!VIPER_TEXT!>set<!>(i: Int, value: Int) {
        data[i] = value
    }
}

// An independent count cannot establish a pure array read's upper bound.
<!VIPER_TEXT!>class ArrayBoundProbe<!>(var data: @Unique IntArray, var count: Int) {
    init { require(0 <= count && count <= data.size) }
    @Law @Pure @Unique fun representation(): Boolean = 0 <= count
    <!VIPER_VERIFICATION_ERROR!>@Pure @Unique fun <!VIPER_TEXT!>getByCount<!>(i: Int): Int =
        if (0 <= i && i < count) data[i] else 0<!>
}

class IntNode(var value: Int, var next: @Unique IntNode?)
// The recursive measure has no bound on its tail, so its addition is overflow-negative.
@Pure fun <!VIPER_TEXT!>nodeSize<!>(node: @Unique IntNode?): Int {
    preconditions { decreases(node) }
    return if (node == null) 0 else <!INTEGER_OVERFLOW!>1 + nodeSize(node.next)<!>
}

// Saturating length keeps the observer total; additions require spare capacity.
@Pure fun <!VIPER_TEXT!>boundedNodeSize<!>(node: @Unique IntNode?): Int {
    preconditions { decreases(node) }
    postconditions<Int> { result -> result >= 0 }
    if (node == null) return 0
    val tail = boundedNodeSize(node.next)
    return if (tail < 2147483647) 1 + tail else 2147483647
}
@Pure fun <!VIPER_TEXT!>nodeGet<!>(node: @Unique IntNode?, i: Int): Int {
    preconditions { decreases(node) }
    return if (node == null || i < 0) 0 else if (i == 0) node.value else nodeGet(node.next, i - 1)
}

fun <!VIPER_TEXT!>nonnegativeNodes<!>(node: @Unique @Borrowed IntNode?) {
    postconditions<Unit> {
        boundedNodeSize(node) >= 0
        boundedNodeSize(node) == old(boundedNodeSize(node))
        forAll<Int> { k -> nodeGet(node, k) == old(nodeGet(node, k)) }
    }
    if (node != null) nonnegativeNodes(node.next)
}

fun <!VIPER_TEXT!>appendNode<!>(node: @Unique IntNode?, value: Int): @Unique IntNode {
    preconditions { boundedNodeSize(node) < 2147483647 }
    postconditions<IntNode> { result ->
        boundedNodeSize(result) == old(boundedNodeSize(node)) + 1
        nodeGet(result, old(boundedNodeSize(node))) == value
        forAll<Int> { k -> (0 <= k && k < old(boundedNodeSize(node))) implies (nodeGet(result, k) == old(nodeGet(node, k))) }
    }
    if (node == null) return IntNode(value, null)
    nonnegativeNodes(node.next)
    node.next = appendNode(node.next, value)
    return node
}

fun <!VIPER_TEXT!>setNode<!>(node: @Unique @Borrowed IntNode?, i: Int, value: Int) {
    preconditions { 0 <= i && i < boundedNodeSize(node) }
    postconditions<Unit> {
        boundedNodeSize(node) == old(boundedNodeSize(node))
        nodeGet(node, i) == value
        forAll<Int> { k -> (0 <= k && k < boundedNodeSize(node) && k != i) implies (nodeGet(node, k) == old(nodeGet(node, k))) }
    }
    if (node != null) {
        if (i == 0) node.value = value
        else setNode(node.next, i - 1, value)
    }
}

<!VIPER_TEXT!>class NodeIntList<!>(var head: @Unique IntNode?, var count: Int) : IntList {
    init { require(count >= 0 && count == boundedNodeSize(head)) }
    @Law @Pure @Unique fun representation(): Boolean = count >= 0 && count == boundedNodeSize(head)
    @Pure @Unique override fun <!VIPER_TEXT!>size<!>(): Int = count
    @Pure @Unique override fun <!VIPER_TEXT!>get<!>(i: Int): Int = nodeGet(head, i)
    @Unique @Borrowed override fun <!VIPER_TEXT!>add<!>(value: Int) {
        head = appendNode(head, value)
        count++
    }
    @Unique @Borrowed override fun <!VIPER_TEXT!>set<!>(i: Int, value: Int) {
        setNode(head, i, value)
    }
}

fun <!VIPER_TEXT!>interfaceClient<!>(list: @Unique @Borrowed IntList) {
    preconditions { list.size() <= 2147483645 }
    val n = list.size()
    verify(n >= 0)
    list.add(7)
    list.add(8)
    val first = get(list, n)
    val second = get(list, n + 1)
    verify(first == 7)
    verify(second == 8)
    list.set(n, 9)
    val changed = get(list, n)
    val unchanged = get(list, n + 1)
    verify(changed == 9)
    verify(unchanged == 8)
    verify(list.size() == n + 2)
    refute(false)
}

fun <!VIPER_TEXT!>lendClient<!>(list: @Unique @Borrowed IntList) {
    preconditions { list.size() < 2147483647 }
    postconditions<Unit> {
        list.size() == old(list.size()) + 1
        list.get(old(list.size())) == 42
        forAll<Int> { k -> (0 <= k && k < old(list.size())) implies (list.get(k) == old(list.get(k))) }
    }
    list.add(42)
}

fun <!VIPER_TEXT!>createUpcastArray<!>() {
    val list: @Unique ArrayIntList = ArrayIntList(IntArray(0), 0)
    lendClient(list)
    verify(list.count == 1)
    verify(list.get(0) == 42)
    val view: @Unique IntList = list
    verify(view.size() == 1)
    verify(view.get(0) == 42)
    interfaceClient(view)
    refute(false)
}

fun <!VIPER_TEXT!>createUpcastNode<!>() {
    val list: @Unique NodeIntList = NodeIntList(null, 0)
    lendClient(list)
    verify(list.count == 1)
    verify(list.get(0) == 42)
    val view: @Unique IntList = list
    verify(view.size() == 1)
    verify(view.get(0) == 42)
    interfaceClient(view)
    refute(false)
}

fun <!VIPER_TEXT!>concreteArrayOperations<!>() {
    val list: @Unique ArrayIntList = ArrayIntList(IntArray(0), 0)
    list.add(11)
    list.add(12)
    list.set(0, 13)
    verify(list.count == 2)
    verify(list.get(0) == 13)
    verify(list.get(1) == 12)
    lendClient(list)
    verify(list.get(0) == 13)
    verify(list.get(1) == 12)
    verify(list.get(2) == 42)
    refute(false)
}

fun <!VIPER_TEXT!>concreteNodeOperations<!>() {
    val list: @Unique NodeIntList = NodeIntList(null, 0)
    list.add(11)
    list.add(12)
    list.set(0, 13)
    verify(list.count == 2)
    verify(list.get(0) == 13)
    verify(list.get(1) == 12)
    lendClient(list)
    verify(list.get(0) == 13)
    verify(list.get(1) == 12)
    verify(list.get(2) == 42)
    refute(false)
}

// Snapshot preservation through invariant-bearing constructors is a translation-gap probe.
fun <!VIPER_TEXT!>arrayConstructionSnapshot<!>() {
    val data: @Unique IntArray = IntArray(1)
    data[0] = 11
    val list: @Unique ArrayIntList = ArrayIntList(data, 1)
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>list.get(0) == 11<!>)
}

fun <!VIPER_TEXT!>nodeConstructionSnapshot<!>() {
    val head: @Unique IntNode = IntNode(11, null)
    val list: @Unique NodeIntList = NodeIntList(head, 1)
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>list.get(0) == 11<!>)
}

fun <!VIPER_TEXT!>badSetIndex<!>(list: @Unique @Borrowed IntList) {
    <!VIPER_VERIFICATION_ERROR!>list.set(list.size(), 1)<!>
}

fun <!VIPER_TEXT!>badGetIndex<!>(list: @Unique @Borrowed IntList) {
    <!VIPER_VERIFICATION_ERROR!>list.get(-1)<!>
}

fun <!VIPER_TEXT!>falseClientClaim<!>(list: @Unique @Borrowed IntList) {
    preconditions { list.size() < 2147483647 }
    val n = list.size()
    list.add(7)
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>list.get(n) == 8<!>)
}

<!VERIFICATION_CONDITION_NOT_ESTABLISHED!><!VIPER_TEXT!>class BrokenIntList<!>(var count: Int, var item: Int) : IntList {
    @Pure @Unique override fun <!VIPER_TEXT!>size<!>(): Int = count
    @Pure @Unique override fun <!VIPER_TEXT!>get<!>(i: Int): Int = item
    <!VERIFICATION_CONDITION_NOT_ESTABLISHED!>@Unique @Borrowed override fun <!VIPER_TEXT!>add<!>(value: Int) {
        count++
        item = value
    }<!>
    <!VERIFICATION_CONDITION_NOT_ESTABLISHED!>@Unique @Borrowed override fun <!VIPER_TEXT!>set<!>(i: Int, value: Int) {
        item = value
    }<!>
}<!>

// FULL_JDK
// RENDER_PREDICATES
import org.jetbrains.kotlin.formver.plugin.*

<!VIPER_TEXT!>class GeneralToken<!>(val flag: Boolean)

interface TruthView {
    @Pure @Unique fun <!VIPER_TEXT!>truth<!>(): Boolean
    @Law @Pure @Unique fun truthful(): Boolean = truth()
}

interface DetailedView : TruthView {
    @Pure @Unique fun <!VIPER_TEXT!>matches<!>(first: Boolean, second: Boolean, token: GeneralToken?): Boolean
    @Pure @Unique fun <!VIPER_TEXT!>token<!>(): GeneralToken?
}

interface OtherView {
    @Pure @Unique fun <!VIPER_TEXT!>other<!>(): Boolean
    @Law @Pure @Unique fun otherLaw(): Boolean = other()
}

<!VIPER_TEXT!>class GeneralCell<!>(var enabled: Boolean, val stored: GeneralToken?) : DetailedView, OtherView {
    init { require(enabled) }
    @Law @Pure @Unique fun representation(): Boolean = enabled
    @Pure @Unique override fun <!VIPER_TEXT!>truth<!>(): Boolean = enabled
    @Pure @Unique override fun <!VIPER_TEXT!>other<!>(): Boolean = enabled
    @Pure @Unique override fun <!VIPER_TEXT!>matches<!>(first: Boolean, second: Boolean, token: GeneralToken?): Boolean =
        first == second && token == stored
    @Pure @Unique override fun <!VIPER_TEXT!>token<!>(): GeneralToken? = stored
}

fun <!VIPER_TEXT!>inheritedTruth<!>(view: @Unique @Borrowed DetailedView) {
    verify(view.truth())
    refute(false)
}

fun <!VIPER_TEXT!>otherTruth<!>(view: @Unique @Borrowed OtherView) {
    verify(view.other())
    refute(false)
}

fun <!VIPER_TEXT!>generalBindings<!>(token: GeneralToken?) {
    val cell: @Unique GeneralCell = GeneralCell(true, token)
    inheritedTruth(cell)
    otherTruth(cell)
    verify(cell.truth())
    val view: @Unique DetailedView = cell
    verify(view.truth())
    verify(view.token() == token)
    verify(view.matches(true, true, token))
    verify(!view.matches(true, false, token))
    refute(false)
}

fun <!VIPER_TEXT!>falseGeneralBinding<!>() {
    val cell: @Unique GeneralCell = GeneralCell(true, null)
    val view: @Unique DetailedView = cell
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>view.matches(true, false, null)<!>)
}

<!VERIFICATION_CONDITION_NOT_ESTABLISHED!><!VIPER_TEXT!>class FalseTruth<!> : DetailedView {
    @Pure @Unique override fun <!VIPER_TEXT!>truth<!>(): Boolean = false
    @Pure @Unique override fun <!VIPER_TEXT!>matches<!>(first: Boolean, second: Boolean, token: GeneralToken?): Boolean = true
    @Pure @Unique override fun <!VIPER_TEXT!>token<!>(): GeneralToken? = null
}<!>

<!VERIFICATION_CONDITION_NOT_ESTABLISHED!><!VIPER_TEXT!>class FalseOther<!> : TruthView, OtherView {
    @Pure @Unique override fun <!VIPER_TEXT!>truth<!>(): Boolean = true
    @Pure @Unique override fun <!VIPER_TEXT!>other<!>(): Boolean = false
}<!>

<!VIPER_TEXT!>class ViewHolder<!>(var view: @Unique DetailedView)

fun <!VIPER_TEXT!>readDetailed<!>(view: @Unique @Borrowed DetailedView): GeneralToken? {
    postconditions<GeneralToken?> { result ->
        result == old(view.token())
        view.token() == old(view.token())
        forAll<Boolean> { a -> forAll<Boolean> { b ->
            view.matches(a, b, null) == old(view.matches(a, b, null))
        } }
    }
    return view.token()
}

fun <!VIPER_TEXT!>lendAndReplaceField<!>(holder: @Unique @Borrowed ViewHolder, replacement: @Unique DetailedView) {
    val originalToken = holder.view.token()
    val originalMatch = holder.view.matches(true, false, null)
    val lentToken = readDetailed(holder.view)
    verify(lentToken == originalToken)
    verify(holder.view.token() == originalToken)
    verify(holder.view.matches(true, false, null) == originalMatch)
    val replacementToken = replacement.token()
    val replacementMatch = replacement.matches(false, false, null)
    holder.view = replacement
    verify(holder.view.token() == replacementToken)
    verify(holder.view.matches(false, false, null) == replacementMatch)
    inheritedTruth(holder.view)
    verify(holder.view.truth())
    refute(false)
}

interface ViewConsumer {
    @SpecOf("accept") @Unique @Borrowed
    fun acceptSpec(view: @Unique @Borrowed DetailedView) {
        postconditions<Boolean> { result ->
            result == old(view.truth())
            view.token() == old(view.token())
        }
    }
    @Unique @Borrowed fun <!VIPER_TEXT!>accept<!>(view: @Unique @Borrowed DetailedView): Boolean
}

<!VIPER_TEXT!>class HonestConsumer<!> : ViewConsumer {
    @Unique @Borrowed
    override fun <!VIPER_TEXT!>accept<!>(view: @Unique @Borrowed DetailedView): Boolean = view.truth()
}

<!VIPER_TEXT!>class DishonestConsumer<!> : ViewConsumer {
    @Unique @Borrowed
    override fun <!VIPER_TEXT!>accept<!>(view: @Unique @Borrowed DetailedView): Boolean = <!VERIFICATION_CONDITION_NOT_ESTABLISHED!>false<!>
}

fun <!VIPER_TEXT!>consumerClient<!>(consumer: @Unique @Borrowed ViewConsumer, view: @Unique @Borrowed DetailedView) {
    val token = view.token()
    val accepted = consumer.accept(view)
    verify(accepted)
    verify(view.token() == token)
    verify(view.truth())
    refute(false)
}

<!VIPER_TEXT!>class BoolNode<!>(var flag: Boolean, var next: @Unique BoolNode?)
@Pure fun <!VIPER_TEXT!>allTrue<!>(node: @Unique BoolNode?): Boolean {
    preconditions { decreases(node) }
    return if (node == null) true else node.flag && allTrue(node.next)
}

interface ChainView {
    @Pure @Unique fun <!VIPER_TEXT!>all<!>(): Boolean
}
<!VIPER_TEXT!>class BoolChain<!>(var head: @Unique BoolNode?) : ChainView {
    @Pure @Unique override fun <!VIPER_TEXT!>all<!>(): Boolean = allTrue(head)
}

fun <!VIPER_TEXT!>recursiveView<!>(head: @Unique BoolNode?) {
    val chain: @Unique BoolChain = BoolChain(head)
    val before = chain.all()
    val view: @Unique ChainView = chain
    verify(view.all() == before)
    refute(false)
}

fun <!VIPER_TEXT!>recursiveConcreteView<!>() {
    val tail: @Unique BoolNode = BoolNode(false, null)
    val head: @Unique BoolNode = BoolNode(true, tail)
    val chain: @Unique BoolChain = BoolChain(head)
    val view: @Unique ChainView = chain
    verify(!view.all())
    refute(false)
}

fun <!VIPER_TEXT!>recursiveFalseClaim<!>() {
    val node: @Unique BoolNode = BoolNode(false, null)
    val chain: @Unique BoolChain = BoolChain(node)
    val view: @Unique ChainView = chain
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>view.all()<!>)
}

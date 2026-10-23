package org.jetbrains.kotlin.formver.common

import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.codeMetaInfo.clearTextFromDiagnosticMarkup
import org.jetbrains.kotlin.lexer.KotlinLexer
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.Closeable
import java.io.File
import java.security.MessageDigest
import kotlin.test.*

/** Source-only correspondence checks; no verifier or symbol resolution is involved. */
class PortDeviationChecker : Closeable {
    private val disposable = Disposer.newDisposable()
    private val environment = KotlinCoreEnvironment.createForProduction(
        disposable, CompilerConfiguration(), EnvironmentConfigFiles.JVM_CONFIG_FILES,
    )
    private val factory = KtPsiFactory(environment.project, false)
    private val erasedAnnotations = setOf("Pure", "Unique", "Borrowed", "WrappingIntArithmetic", "WrappingLongArithmetic", "TrustedElementEquality")

    override fun close() = Disposer.dispose(disposable)

    /** Reconstruct the source compiled by the diagnostics test framework. */
    fun portSource(marked: String): String = clearTextFromDiagnosticMarkup(marked)

    private fun parse(source: String): KtFile = factory.createFile(source).also {
        val errors = PsiTreeUtil.collectElementsOfType(it, PsiErrorElement::class.java)
        require(errors.isEmpty()) { "Kotlin parse errors: ${errors.map { error -> error.errorDescription }}" }
    }

    /** Erase only specification statements/members and the named annotations, retaining executable text. */
    fun strip(source: String): String {
        val file = parse(source)
        val ranges = mutableListOf<IntRange>()
        fun erase(element: PsiElement) {
            var start = element.textRange.startOffset
            var end = element.textRange.endOffset
            val lineStart = source.lastIndexOf('\n', start - 1) + 1
            val lineEnd = source.indexOf('\n', end).let { if (it < 0) source.length else it }
            if (source.substring(lineStart, start).isBlank() && source.substring(end, lineEnd).isBlank()) {
                start = lineStart
                end = if (lineEnd < source.length) lineEnd + 1 else lineEnd
            } else {
                // Spaces adjoining a removed inline annotation/statement belong to that syntax.
                while (end < source.length && source[end] == ' ') end++
            }
            ranges += start until end
        }
        for (declaration in PsiTreeUtil.collectElementsOfType(file, KtDeclaration::class.java)) {
            if (declaration.annotationEntries.any { it.shortName?.asString() in setOf("Law", "SpecOf") }) erase(declaration)
        }
        for (annotation in PsiTreeUtil.collectElementsOfType(file, KtAnnotationEntry::class.java)) {
            if (annotation.shortName?.asString() in erasedAnnotations) erase(annotation)
        }
        for (block in PsiTreeUtil.collectElementsOfType(file, KtBlockExpression::class.java)) {
            var leading = true
            for (statement in block.statements) {
                val call = statement as? KtCallExpression
                val name = (call?.calleeExpression as? KtNameReferenceExpression)?.getReferencedName()
                val specificationBlock = call?.lambdaArguments?.size == 1 && call.valueArgumentList?.arguments.isNullOrEmpty()
                val contract = specificationBlock && name in setOf("preconditions", "postconditions") &&
                    leading && block.parent is KtDeclarationWithBody
                val loop = specificationBlock && name == "loopInvariants" && block.parent is KtContainerNodeForControlStructureBody &&
                    block.parent.parent is KtLoopExpression
                val verification = name == "verify" && call?.valueArgumentList != null && call.lambdaArguments.isEmpty()
                if (contract || loop || verification) erase(statement)
                if (!contract) leading = false
            }
        }
        val merged = mutableListOf<IntRange>()
        for (range in ranges.sortedBy { it.first }) {
            val previous = merged.lastOrNull()
            if (previous != null && range.first <= previous.last + 1) {
                merged[merged.lastIndex] = previous.first..maxOf(previous.last, range.last)
            } else merged += range
        }
        return buildString {
            var cursor = 0
            for (range in merged) {
                append(source, cursor, range.first)
                cursor = range.last + 1
            }
            append(source, cursor, source.length)
        }
    }

    fun declarations(source: String): List<Pair<String?, String>> =
        parse(portSource(source)).declarations.map { (it as? KtNamedDeclaration)?.name to it.text }

    /** Normalize regression names, retaining annotations, parameter/return types and the body. */
    fun normalizedFunction(source: String, name: String): String {
        val function = PsiTreeUtil.collectElementsOfType(parse(portSource(source)), KtNamedFunction::class.java)
            .single { it.name == name }
        val keyword = checkNotNull(function.funKeyword).textRange.startOffset
        val identifier = checkNotNull(function.nameIdentifier).textRange
        val declaration = function.text.substring(keyword - function.textRange.startOffset)
            .replaceRange(identifier.startOffset - keyword, identifier.endOffset - keyword, "search")
        return function.annotationEntries.joinToString("\n") { it.text } + "\n" + declaration
    }

    fun functionLines(marked: String, name: String): IntRange {
        val source = portSource(marked)
        val function = PsiTreeUtil.collectElementsOfType(parse(source), KtNamedFunction::class.java).single { it.name == name }
        fun line(offset: Int) = 1 + source.take(offset).count { it == '\n' }
        return line(function.textRange.startOffset)..line(function.textRange.endOffset)
    }

    /** Zero-context unified hunks, deterministic even when several LCS alignments exist. */
    fun strippedDiff(upstream: String, port: String): String {
        fun lines(text: String) = text.split('\n')
        val a = lines(strip(upstream))
        val b = lines(strip(port))
        val lcs = Array(a.size + 1) { IntArray(b.size + 1) }
        for (i in a.indices.reversed()) for (j in b.indices.reversed()) {
            lcs[i][j] = if (a[i] == b[j]) 1 + lcs[i + 1][j + 1] else maxOf(lcs[i + 1][j], lcs[i][j + 1])
        }
        return buildString {
            var i = 0
            var j = 0
            while (i < a.size || j < b.size) {
                if (i < a.size && j < b.size && a[i] == b[j]) {
                    i++
                    j++
                    continue
                }
                val startA = i
                val startB = j
                val body = StringBuilder()
                while (i < a.size || j < b.size) {
                    if (i < a.size && j < b.size && a[i] == b[j]) break
                    if (i < a.size && (j == b.size || lcs[i + 1][j] >= lcs[i][j + 1])) {
                        body.append('-').append(a[i++]).append('\n')
                    } else body.append('+').append(b[j++]).append('\n')
                }
                val countA = i - startA
                val countB = j - startB
                append("@@ -${if (countA == 0) startA else startA + 1},$countA +${if (countB == 0) startB else startB + 1},$countB @@\n")
                append(body)
            }
        }
    }

    /** Every hunk has an immediately preceding '# D-n: explanation' line. */
    fun check(upstream: String, port: String, approvedDiff: String) {
        val lines = approvedDiff.lines()
        val label = Regex("# D-[1-8]: .+")
        for ((index, line) in lines.withIndex()) {
            if (line.startsWith("@@")) require(index > 0 && label.matches(lines[index - 1])) {
                "Unlabelled deviation hunk: $line"
            }
            if (line.startsWith("#")) require(label.matches(line) && lines.getOrNull(index + 1)?.startsWith("@@") == true) {
                "Invalid deviation label: $line"
            }
        }
        val expected = lines.filterNot { label.matches(it) }.joinToString("\n")
        val actual = strippedDiff(upstream, port)
        check(actual == expected) { "Stripped port differs from approved deviations:\n$actual" }
    }

    private fun signature(owner: PsiElement): String = when (owner) {
        is KtNamedFunction -> "fun ${owner.receiverTypeReference?.let { "${it.text}." }.orEmpty()}${owner.name}(${owner.valueParameters.joinToString(",") { it.typeReference?.text.orEmpty() }})"
        is KtSecondaryConstructor -> "constructor(${owner.valueParameters.joinToString(",") { it.typeReference!!.text }})"
        is KtClassInitializer -> "init"
        is KtPropertyAccessor -> "${owner.property.name}.${if (owner.isGetter) "get" else "set"}"
        is KtProperty -> "property(${owner.name})"
        is KtClassOrObject -> "class(${owner.name})"
        else -> error("Assignment owner: ${owner.text}")
    }

    private fun isStorage(expression: KtExpression?): Boolean = when (val target = expression?.let(KtPsiUtil::deparenthesize)) {
        is KtNameReferenceExpression -> target.getReferencedName() == "elementData"
        is KtDotQualifiedExpression -> {
            val receiver = KtPsiUtil.deparenthesize(target.receiverExpression)
            val selector = target.selectorExpression as? KtNameReferenceExpression
            receiver is KtThisExpression && selector?.getReferencedName() == "elementData"
        }
        else -> false
    }

    private fun isSiteOwner(element: PsiElement): Boolean = when (element) {
        is KtNamedFunction, is KtSecondaryConstructor, is KtClassInitializer,
        is KtPropertyAccessor, is KtProperty, is KtClassOrObject -> true
        else -> false
    }

    private fun ownerIdentity(element: PsiElement): String = generateSequence(element.parent) { it.parent }
        .filter(::isSiteOwner).toList().asReversed().joinToString(" / ", transform = ::signature)

    private fun declarationIdentity(element: PsiElement): String =
        listOf(ownerIdentity(element), signature(element)).filter { it.isNotEmpty() }.joinToString(" / ")

    private fun normalizedRhs(expression: KtExpression): List<String> = buildList {
        val lexer = KotlinLexer()
        lexer.start(expression.text)
        while (lexer.tokenType != null) {
            val token = lexer.tokenType
            val text = lexer.tokenText
            if (token != KtTokens.WHITE_SPACE && !KtTokens.COMMENTS.contains(token)) {
                if (token == KtTokens.IDENTIFIER && text.removeSurrounding("`") == "emptyElementData") {
                    addAll(listOf("arrayOfNulls", "(", "0", ")"))
                } else add(text)
            }
            lexer.advance()
        }
    }

    data class AssignmentSite(val owner: String, val operation: String, val rhs: List<String>)

    /** Pin owner, operation, multiplicity and RHS, including current-instance qualified writes. */
    fun assignmentSites(source: String): List<AssignmentSite> {
        val file = parse(strip(source))
        val assignments = PsiTreeUtil.collectElementsOfType(file, KtBinaryExpression::class.java).filter {
            KtTokens.ALL_ASSIGNMENTS.contains(it.operationToken) && isStorage(it.left)
        }.map { assignment ->
            AssignmentSite(ownerIdentity(assignment), assignment.operationReference.text, normalizedRhs(assignment.right!!))
        }
        val unaryWrites = PsiTreeUtil.collectElementsOfType(file, KtUnaryExpression::class.java).filter {
            it.operationToken in setOf(KtTokens.PLUSPLUS, KtTokens.MINUSMINUS) && isStorage(it.baseExpression)
        }.map { AssignmentSite(ownerIdentity(it), it.operationReference.text, emptyList()) }
        return (assignments + unaryWrites).sortedBy { it.toString() }
    }

    fun checkAssignmentSites(source: String, expected: List<AssignmentSite>) {
        val file = parse(strip(source))
        val initializers = PsiTreeUtil.collectElementsOfType(file, KtProperty::class.java).filter {
            it.name == "elementData" && (it.initializer != null || it.hasDelegate())
        }
        check(initializers.isEmpty()) { "elementData property initializers: ${initializers.map { it.text }}" }
        val shadows = PsiTreeUtil.collectElementsOfType(file, KtNamedDeclaration::class.java).filter {
            it.name == "elementData" && !(it is KtProperty && it.parent is KtClassBody)
        }
        check(shadows.isEmpty()) { "elementData shadow declarations: ${shadows.map { it.text }}" }
        check(assignmentSites(source) == expected.sortedBy { it.toString() }) { "elementData assignment sites differ: ${assignmentSites(source)}" }
    }

    /** D-7 permits removed members; every retained member keeps its assignment sites. */
    fun checkPort(upstream: String, port: String, approvedDiff: String) {
        check(upstream, port, approvedDiff)
        val file = parse(strip(port))
        val owners = PsiTreeUtil.collectElementsOfType(file, KtDeclaration::class.java).filter(::isSiteOwner).map(::declarationIdentity)
        val retained = assignmentSites(upstream).filter { it.owner in owners }
        checkAssignmentSites(port, retained)
    }
}

class PortDeviationTest {
    private val data = File("formver.compiler-plugin/testData")
    private fun fixture(name: String) = data.resolve("portcheck/$name").readText()
    private fun checker(action: (PortDeviationChecker) -> Unit) = PortDeviationChecker().use(action)

    @Test
    fun `specifications erase but executable changes fail correspondence`() = checker { checker ->
        val upstream = fixture("base.kt")
        checker.checkPort(upstream, fixture("specification.kt"), "")
        assertFailsWith<IllegalStateException> { checker.check(upstream, fixture("body-change.kt"), "") }
        val diff = fixture("body-change.diff")
        checker.check(upstream, fixture("body-change.kt"), diff)
        assertFailsWith<IllegalArgumentException> {
            checker.check(upstream, fixture("body-change.kt"), diff.substringAfter('\n'))
        }
    }

    @Test
    fun `diagnostic markup clears without hiding executable deviations`() = checker { checker ->
        val base = fixture("base.kt")
        val marked = fixture("marked.kt")
        checker.checkPort(base, checker.portSource(marked), "")
        val changed = marked.replace("return 1", "return 2")
        assertFailsWith<IllegalStateException> { checker.checkPort(base, checker.portSource(changed), "") }
        assertFailsWith<IllegalArgumentException> { checker.checkPort(base, marked, "") }
    }

    @Test
    fun `assignment sites stay pinned even with an approved body diff`() = checker { checker ->
        val base = fixture("base.kt")
        for (changed in listOf(
            fixture("assignment-change.kt"),
            fixture("local-function.kt"),
            base.replace("        elementData = arrayOfNulls(1)\n", ""),
            base.replace("lateinit var elementData: Array<Any?>", "var elementData: Array<Any?> = arrayOfNulls(0)"),
            base.replace("lateinit var elementData: Array<Any?>", "var elementData: Array<Any?> by storage"),
        )) {
            val approved = approve(checker, base, changed)
            checker.check(base, changed, approved)
            assertFailsWith<IllegalStateException> { checker.checkPort(base, changed, approved) }
        }
        val removed = base.replace("    fun reset() {\n        elementData = arrayOfNulls(1)\n    }\n", "")
        checker.checkPort(base, removed, "# D-7: fixture removed member\n" + checker.strippedDiff(base, removed))
    }

    private fun approve(checker: PortDeviationChecker, base: String, changed: String): String =
        checker.strippedDiff(base, changed).replace(Regex("(?m)^@@"), "# D-6: fixture write-site change\n@@")

    @Test
    fun `qualified existing sites match but extra and replacement writes fail`() = checker { checker ->
        val base = fixture("base.kt")
        for (target in listOf("this.elementData", "this@Fixture.`elementData`", "(this).elementData", "(elementData)", "`elementData`")) {
            val qualified = base.replace("elementData = arrayOfNulls(1)", "$target = arrayOfNulls(1)")
            checker.checkPort(base, qualified, approve(checker, base, qualified))
            val replacement = qualified.replace("arrayOfNulls(1)", "arrayOfNulls(2)")
            val extra = qualified.replace("$target = arrayOfNulls(1)", "$target = arrayOfNulls(1); $target = arrayOfNulls(2)")
            for (changed in listOf(replacement, extra)) {
                val approved = approve(checker, base, changed)
                checker.check(base, changed, approved)
                assertFailsWith<IllegalStateException> { checker.checkPort(base, changed, approved) }
            }
        }
    }

    @Test
    fun `compound and nested writes cannot bypass the independent pin`() = checker { checker ->
        val base = fixture("numeric.kt")
        checker.checkPort(base, base, "")
        for (operator in listOf("+=", "-=", "*=", "/=", "%=")) {
            for (target in listOf("elementData", "this.elementData")) {
                val changed = base.replace("    fun reset() {\n        elementData = 8", "    fun reset() {\n        $target $operator 8")
                val approved = approve(checker, base, changed)
                checker.check(base, changed, approved)
                assertFailsWith<IllegalStateException> { checker.checkPort(base, changed, approved) }
            }
        }
        for (write in listOf("this.elementData++", "--elementData", "run { this.elementData = 9 }")) {
            val changed = base.replace("    fun reset() {", "    fun reset() {\n        $write")
            val approved = approve(checker, base, changed)
            checker.check(base, changed, approved)
            assertFailsWith<IllegalStateException> { checker.checkPort(base, changed, approved) }
        }
    }

    @Test
    fun `shadow declarations cannot substitute local writes for field writes`() = checker { checker ->
        val base = fixture("base.kt")
        checker.checkPort(base, base, "")
        for (changed in listOf(
            base.replace("        elementData =", "        var elementData: Array<Any?>\n        elementData ="),
            base.replace("        elementData =", "        val (elementData, other) = pair\n        elementData ="),
            base.replace("fun reset()", "fun reset(elementData: Array<Any?>)"),
        )) {
            val approved = approve(checker, base, changed)
            checker.check(base, changed, approved)
            assertFailsWith<IllegalStateException> { checker.checkPort(base, changed, approved) }
        }
    }

    @Test
    fun `indexed mutations require diff approval but are outside reference assignment pinning`() = checker { checker ->
        val base = fixture("base.kt")
        for (write in listOf("elementData[0] = null", "this.elementData[0] += 1", "elementData[0]++")) {
            val changed = base.replace("        val marker = 0", "        $write\n        val marker = 0")
            assertFailsWith<IllegalStateException> { checker.checkPort(base, changed, "") }
            checker.checkPort(base, changed, approve(checker, base, changed))
        }
    }

    @Test
    fun `rhs normalization preserves executable literal contents`() = checker { checker ->
        val base = fixture("base.kt").replace("arrayOfNulls(1)", "arrayOf(\"emptyElementData with space\")")
        for (value in listOf("arrayOf(\"arrayOfNulls(0) with space\")", "arrayOf(\"emptyElementDatawithspace\")")) {
            val changed = base.replace("arrayOf(\"emptyElementData with space\")", value)
            val approved = approve(checker, base, changed)
            checker.check(base, changed, approved)
            assertFailsWith<IllegalStateException> { checker.checkPort(base, changed, approved) }
        }
        val overloaded = fixture("base.kt").replace("fun reset()", "fun reset(extra: Int)")
        checker.check(fixture("base.kt"), overloaded, approve(checker, fixture("base.kt"), overloaded))
        assertFailsWith<IllegalStateException> {
            checker.checkPort(fixture("base.kt"), overloaded, approve(checker, fixture("base.kt"), overloaded))
        }
        val tokens = fixture("base.kt").replace("arrayOfNulls(1)", "a in b")
        val joined = tokens.replace("a in b", "ainb")
        checker.check(tokens, joined, approve(checker, tokens, joined))
        assertFailsWith<IllegalStateException> { checker.checkPort(tokens, joined, approve(checker, tokens, joined)) }
        val sentinel = fixture("base.kt").replace("arrayOfNulls(1)", "emptyElementData")
        val port = sentinel.replace("= emptyElementData", "= arrayOfNulls(0)")
        checker.checkPort(sentinel, port, approve(checker, sentinel, port))
    }

    @Test
    fun `non specification syntax remains visible`() = checker { checker ->
        val base = fixture("base.kt")
        for (changed in listOf(
            base.replace("return 1", "println(\"verify(fake)\"); return 1"),
            base.replace("return 1", "preconditions { false }; return 1"),
            base.replace("val marker = 0", "preconditions(false)\n        val marker = 0"),
            base.replace("return 1", "@Deprecated(\"reason\") fun local() = 2\n        return 1"),
            base.replace("return 1", "other.verify(false); return 1"),
            base.replace("return 1", "loopInvariants { false }; return 1"),
            base.replace("fun answer", "@InlineOnly fun answer"),
            base.replace("return 1", "// verify(false)\n        return 1"),
            base.removeSuffix("\n"),
        )) assertFailsWith<IllegalStateException> { checker.check(base, changed, "") }
    }

    @Test
    fun `vendored assignment sites and source hash are pinned`() = checker { checker ->
        val upstream = data.resolve("upstream/kotlin-v2.3.0/ArrayDeque.kt").readBytes()
        val hash = MessageDigest.getInstance("SHA-256").digest(upstream).joinToString("") { "%02x".format(it) }
        assertEquals("1369874f80ec51c6c484276aacc53fe4a401870b1de0e907cc1217278d6c40ae", hash)
        val sites = checker.assignmentSites(upstream.toString(Charsets.UTF_8))
        assertEquals(
            listOf("constructor()", "constructor(Collection<E>)", "constructor(Collection<E>)", "constructor(Int)", "fun copyElements(Int)", "fun ensureCapacity(Int)")
                .map { "class(ArrayDeque) / $it" },
            sites.map { it.owner },
        )
    }

    @Test
    fun `search mutants change only their wrapped guards and fail the intended clauses`() = checker { checker ->
        val directory = data.resolve("diagnostics/targets/arraydeque")
        val port = directory.resolve("ArrayDeque.kt").readText()
        val cases = listOf(
            Triple("indexOf", "indexOfPre80390", "head >= tail"),
            Triple("lastIndexOf", "lastIndexOfPre80661", "head > tail"),
        )
        for ((original, mutant, guard) in cases) {
            val source = directory.resolve("$mutant.kt").readText()
            val function = checker.normalizedFunction(source, mutant)
            assertTrue(function.contains("@TrustedElementEquality"), mutant)
            val changes = checker.strippedDiff(checker.normalizedFunction(port, original), function)
                .lines().filter { it.startsWith("-") || it.startsWith("+") }
            assertEquals(
                listOf("-        } else if (isNotEmpty() && head >= tail) {", "+        } else if ($guard) {"),
                changes, mutant,
            )
            val diagnostics = directory.resolve("$mutant.viper.diag.txt").readText()
            val clauses = Regex("(?m)^Failing clause: `([^`]+)`").findAll(diagnostics).map { it.groupValues[1] }.toSet()
            val intended = if (original == "indexOf") "result < size" else
                "(result == -1) == !exists<Int> { i -> (0 <= i && i < size) && element == element..."
            assertEquals(setOf(intended), clauses, mutant)
            val errors = diagnostics.lineSequence().count { it.contains(": error:") }
            val failures = Regex("(?m)^Failing clause:").findAll(diagnostics).count()
            assertEquals(failures, errors, mutant)
        }
        val untrusted = checker.normalizedFunction(port, "indexOfUntrusted")
        assertFalse(untrusted.contains("@TrustedElementEquality"))
        assertEquals("", checker.strippedDiff(checker.normalizedFunction(port, "indexOf"), untrusted))
        val untrustedLines = checker.functionLines(port, "indexOfUntrusted")
        val clauses = Regex("(?m)^Failing clause: `([^`]+)` \\((\\d+):\\d+\\)")
            .findAll(directory.resolve("ArrayDeque.viper.diag.txt").readText())
            .filter { it.groupValues[2].toInt() in untrustedLines }.map { it.groupValues[1] }.toSet()
        assertEquals(
            setOf(
                "(result == -1) == !exists<Int> { i -> (0 <= i && i < size) && element == element...",
                "(result >= 0) implies (element == elementAt(result))",
                "forAll<Int> { i -> (0 <= i && i < result) implies (element != elementAt(i)) }",
            ), clauses,
        )
    }

    @Test
    fun `real port matches labelled deviations and retained assignment sites`() {
        val port = data.resolve("diagnostics/targets/arraydeque/ArrayDeque.kt")
        assumeTrue(port.exists(), "ArrayDeque port is absent")
        checker { checker ->
            val upstream = data.resolve("upstream/kotlin-v2.3.0/ArrayDeque.kt").readText()
            val source = checker.portSource(port.readText())
            assertEquals(setOf("class(ArrayDeque) / constructor()", "class(ArrayDeque) / constructor(Int)"),
                checker.assignmentSites(source).map { it.owner }.filter { "constructor(" in it }.toSet())
            checker.checkPort(upstream, source, port.resolveSibling("ArrayDeque.port.diff").readText())
        }
    }
}

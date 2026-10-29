package org.jetbrains.kotlin.formver.common

import com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.codeMetaInfo.clearTextFromDiagnosticMarkup
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.psi.*
import java.io.Closeable
import java.io.File
import kotlin.test.*

/** Compare the public-observer projection of the proved port with its bundled binding. */
private class DequeContracts : Closeable {
    private val disposable = Disposer.newDisposable()
    @OptIn(org.jetbrains.kotlin.K1Deprecation::class)
    private val environment = KotlinCoreEnvironment.createForProduction(
        disposable, CompilerConfiguration(), EnvironmentConfigFiles.JVM_CONFIG_FILES,
    )
    private val factory = KtPsiFactory(environment.project, false)
    override fun close() = Disposer.dispose(disposable)

    private fun KtNamedFunction.target(): String? = annotationEntries
        .singleOrNull { it.shortName?.asString() == "SpecOf" }
        ?.valueArguments?.single()?.getArgumentExpression()?.text?.removeSurrounding("\"")

    private fun contracts(function: KtNamedFunction): List<KtCallExpression> =
        function.bodyBlockExpression?.statements.orEmpty().takeWhile {
            it is KtCallExpression && it.calleeExpression?.text in setOf("preconditions", "postconditions")
        }.map { it as KtCallExpression }

    private fun normalize(text: String): String = text
        .replace("@Unique ", "")
        .replace("triggers(elementAt(i), old(elementAt(i))); ", "")
        .replace("elementAt", "get").replace("ArrayDequeModel", "ArrayDeque")
        .replace(Regex("\\s+"), "")

    private val representationClauses = setOf(
        "elementData.size == old(elementData.size)",
        "head == old(head)",
        "elementData[old(head)] === null",
        "elementData[old(internalIndex(lastIndex))] === null",
        "forAll<Int> { i -> triggers(old(internalIndex(i))); (0 <= i && i < old(size)) implies (elementData[old(internalIndex(i))] === null) }",
    ).map(::normalize).toSet()

    private fun contract(call: KtCallExpression, project: Boolean): String {
        val lambda = call.lambdaArguments.single().getLambdaExpression()!!
        val statements = lambda.bodyExpression!!.statements.map { statement ->
            var text = statement.text
            if (project) {
                // These conjunctions have a public result clause and a private null-hygiene clause.
                text = text.replace(" && elementData[old(head)] === null", "")
                    .replace(" && elementData[old(internalIndex(lastIndex))] === null", "")
                if (normalize(text) in representationClauses) return@map null
                require(!Regex("\\b(elementData|head|internalIndex)\\b").containsMatchIn(text)) {
                    "Unclassified representation clause: $text"
                }
            }
            normalize(text)
        }.filterNotNull()
        return normalize(call.calleeExpression!!.text + (call.typeArgumentList?.text ?: "")) +
            "{" + lambda.valueParameters.joinToString { it.text } + "->" + statements.joinToString(";") + "}"
    }

    fun check(port: String, model: String) {
        val portFile = factory.createFile(clearTextFromDiagnosticMarkup(port))
        val modelFile = factory.createFile(model)
        val portClass = portFile.declarations.filterIsInstance<KtClass>().single { it.name == "ArrayDeque" }
        val portInterface = portFile.declarations.filterIsInstance<KtClass>().single { it.name == "DequeSpec" }
        val modelClass = modelFile.declarations.filterIsInstance<KtClass>().single()
        val portFunctions = (portInterface.declarations + portClass.declarations).filterIsInstance<KtNamedFunction>()
        val modelFunctions = modelClass.declarations.filterIsInstance<KtNamedFunction>()
        val publicMembers = portFunctions.filter {
            it.target() == null && !it.hasModifier(org.jetbrains.kotlin.lexer.KtTokens.PRIVATE_KEYWORD) &&
                it.annotationEntries.none { annotation -> annotation.shortName?.asString() == "Law" } &&
                it.name !in setOf("get", "indexOfUntrusted", "removeKeepsSlot", "addLastWithoutCapacity")
        }.associateBy { if (it.name == "elementAt") "get" else it.name }
        val bindings = modelFunctions.filter { function ->
            function.target() == null && function.annotationEntries.none { it.shortName?.asString() == "Law" }
        }
        assertEquals(publicMembers.keys, bindings.map { it.name }.toSet(), "Bound members")
        for (binding in bindings) {
            val original = publicMembers.getValue(binding.name)
            assertEquals(original.valueParameters.map { it.typeReference?.text }, binding.valueParameters.map { it.typeReference?.text })
            assertEquals(original.typeReference?.text ?: "Unit", binding.typeReference?.text ?: "Unit")
            fun ownership(function: KtNamedFunction) = function.annotationEntries.mapNotNull { it.shortName?.asString() }
                .filter { it in setOf("Pure", "Unique", "Borrowed") }.toSet()
            assertEquals(ownership(original), ownership(binding), binding.name)
        }
        fun find(functions: List<KtNamedFunction>, name: String): KtNamedFunction =
            functions.singleOrNull { it.target() == name } ?: functions.single { it.name == name }
        for (function in modelFunctions.filter { it.target() == null }) {
            val name = function.name!!
            val original = find(portFunctions, if (name == "get") "elementAt" else name)
            if (function.annotationEntries.any { it.shortName?.asString() == "Law" }) {
                // The port's capacity invariant is private proof scope; only nonnegative size is a library law.
                assertEquals("sizeBounds", name)
                assertEquals(normalize("0 <= size && size <= 1073741824"), normalize(original.bodyExpression!!.text))
                assertEquals(normalize("0 <= size"), normalize(function.bodyExpression!!.text))
            } else {
                assertEquals(
                    contracts(original).map { contract(it, true) },
                    contracts(find(modelFunctions, name)).map { contract(it, false) }, name,
                )
            }
        }
        val witness = portFile.declarations.filterIsInstance<KtNamedFunction>().single { it.name == "newDeque" }
        val constructor = modelFile.declarations.filterIsInstance<KtNamedFunction>().single { it.target() == "arrayDequeModel" }
        val factory = modelFile.declarations.filterIsInstance<KtNamedFunction>().single { it.name == "arrayDequeModel" }
        assertEquals(witness.typeReference!!.text.replace("ArrayDeque", "ArrayDequeModel"), factory.typeReference!!.text)
        assertEquals(contracts(witness).map { contract(it, false) }, contracts(constructor).map { contract(it, false) })
    }
}

class ArrayDequeSpecEqualityTest {
    private val data = File("formver.compiler-plugin/testData/diagnostics/targets/arraydeque/ArrayDeque.kt")
    private val bundled = File("formver.compiler-plugin/plugin/resources/external-specs/ArrayDeque.kt")

    @Test fun `bundled contracts equal the port public projection`() {
        DequeContracts().use { it.check(data.readText(), bundled.readText()) }
    }

    @Test fun `constructor and mutator contract drift is rejected`() {
        val source = bundled.readText()
        DequeContracts().use { checker ->
            for ((clause, mutant) in listOf(
                "result.size == 0" to "result.size == 1",
                "size == old(size) + 1" to "size == old(size) + 2",
                "Boolean = 0 <= size" to "Boolean = 0 <= size && size <= 1073741824",
                "preconditions { size < 715827882 }" to "preconditions { size < 1073741824 }",
            )) {
                assertFailsWith<AssertionError> { checker.check(data.readText(), source.replace(clause, mutant)) }
            }
        }
    }
}

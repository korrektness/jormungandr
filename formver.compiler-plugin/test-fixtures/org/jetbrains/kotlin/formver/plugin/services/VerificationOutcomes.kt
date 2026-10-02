package org.jetbrains.kotlin.formver.plugin.services

import org.jetbrains.kotlin.KtRealSourceElementKind
import org.jetbrains.kotlin.codeMetaInfo.CodeMetaInfoParser
import org.jetbrains.kotlin.diagnostics.KtDiagnostic
import org.jetbrains.kotlin.diagnostics.KtDiagnosticWithSource
import org.jetbrains.kotlin.diagnostics.KtDiagnosticWithoutSource
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.declarations.FirConstructor
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.declarations.FirPropertyAccessor
import org.jetbrains.kotlin.fir.declarations.FirSimpleFunction
import org.jetbrains.kotlin.fir.visitors.FirDefaultVisitorVoid
import org.jetbrains.kotlin.formver.common.services.updatingTestData
import org.jetbrains.kotlin.formver.core.diagnostics.ConversionErrors
import org.jetbrains.kotlin.formver.plugin.compiler.VerificationErrors
import org.jetbrains.kotlin.test.frontend.fir.FirOutputArtifact
import org.jetbrains.kotlin.test.model.TestFile
import org.jetbrains.kotlin.test.services.TestService
import org.jetbrains.kotlin.test.services.TestServices

/**
 * What a test asserts about one function's verification, as its diagnostic markers encode it.
 */
enum class VerificationOutcome {
    /** No verifier diagnostic and no skip: the function verified, or was not selected for verification. */
    CLEAN,
    FAILED,
    SKIPPED;

    companion object {
        fun ofTag(tag: String): VerificationOutcome? = when (tag) {
            in VerificationErrors.tags() -> FAILED
            ConversionErrors.VERIFICATION_SKIPPED.name -> SKIPPED
            else -> null
        }
    }
}

/** A diagnostic at [offset] in a test file. [message] is known for diagnostics of the current run only. */
data class Finding(val offset: Int, val tag: String, val message: String? = null)

/** A function declaration spanning [start] to [end] in a test file. */
data class FunctionSpan(val name: String, val start: Int, val end: Int) {
    operator fun contains(offset: Int): Boolean = offset in start..<end
}

data class OutcomeChange(
    val function: String,
    val expected: VerificationOutcome,
    val actual: VerificationOutcome,
    val messages: List<String>,
) {
    override fun toString(): String = buildString {
        append("$function: ${expected.name.lowercase()} -> ${actual.name.lowercase()}")
        messages.forEach { append("\n    $it") }
    }
}

/** Thrown instead of regenerating goldens that would change a verification outcome. */
class OutcomeChangeRefused(changes: List<OutcomeChange>) : AssertionError(
    "Regenerating would change these verification outcomes, so no golden was written:\n" +
            changes.joinToString("\n")
)

/**
 * Compares per-function outcomes between the [expected] markers and the [actual] diagnostics. A finding belongs to
 * the innermost function whose span contains it; findings outside every function count as one top-level entry.
 * Without [verified], the run never reached the verifier, so only skips are compared.
 */
fun outcomeChanges(
    functions: List<FunctionSpan>,
    expected: List<Finding>,
    actual: List<Finding>,
    verified: Boolean,
): List<OutcomeChange> {
    fun owner(finding: Finding): FunctionSpan? =
        functions.filter { finding.offset in it }.minByOrNull { it.end - it.start }

    fun outcomes(findings: List<Finding>): Map<FunctionSpan?, VerificationOutcome> =
        findings.mapNotNull { finding ->
            val outcome = VerificationOutcome.ofTag(finding.tag)
                ?.takeIf { verified || it != VerificationOutcome.FAILED }
                ?: return@mapNotNull null
            owner(finding) to outcome
        }.groupBy({ it.first }, { it.second })
            .mapValues { (_, found) -> found.maxBy { it.ordinal } }

    val expectedOutcomes = outcomes(expected)
    val actualOutcomes = outcomes(actual)
    return (expectedOutcomes.keys + actualOutcomes.keys).mapNotNull { function ->
        val was = expectedOutcomes[function] ?: VerificationOutcome.CLEAN
        val now = actualOutcomes[function] ?: VerificationOutcome.CLEAN
        if (was == now) return@mapNotNull null
        val messages = actual.filter { owner(it) == function && VerificationOutcome.ofTag(it.tag) != null }
            .map { "${it.tag}: ${it.message}" }
        OutcomeChange(function?.name ?: "(top level)", was, now, messages)
    }.sortedBy { it.function }
}

/**
 * Refuses [changes] when regenerating goldens, unless [recordOutcomes] allows them. Outside regeneration, the golden
 * comparison reports any change.
 */
fun checkOutcomeChanges(changes: List<OutcomeChange>, updating: Boolean, recordOutcomes: Boolean) {
    if (updating && !recordOutcomes && changes.isNotEmpty()) throw OutcomeChangeRefused(changes)
}

private val recordingOutcomes: Boolean
    get() = System.getProperty("formver.recordOutcomes") == "true"

/**
 * Collects the outcome-relevant diagnostics of a run and checks them against the markers the test file already has.
 */
class VerificationOutcomes(val testServices: TestServices) : TestService {
    private val reported: MutableMap<TestFile, MutableList<Finding>> = mutableMapOf()

    fun reportDiagnostics(file: TestFile, diagnostics: List<KtDiagnostic>) {
        reported.getOrPut(file) { mutableListOf() } += diagnostics.map { diagnostic ->
            val range = when (diagnostic) {
                is KtDiagnosticWithSource -> diagnostic.textRanges.first()
                is KtDiagnosticWithoutSource -> diagnostic.firstRange
            }
            Finding(range.startOffset, diagnostic.factoryName, diagnostic.renderMessage())
        }
    }

    /** Must run before any golden of the test is asserted, since asserting is what rewrites it. */
    fun check(info: FirOutputArtifact, verified: Boolean) {
        val changes = info.partsForDependsOnModules.flatMap { part ->
            part.firFiles.filterKeys { !it.isAdditional }.flatMap { (testFile, firFile) ->
                val expected = CodeMetaInfoParser.getCodeMetaInfoFromText(testFile.originalContent)
                    .map { Finding(it.start, it.tag) }
                val functions = mutableListOf<FunctionSpan>()
                firFile.accept(object : FirDefaultVisitorVoid() {
                    override fun visitElement(element: FirElement) {
                        if (element is FirFunction) element.span()?.let { functions += it }
                        element.acceptChildren(this)
                    }
                })
                outcomeChanges(functions, expected, reported[testFile].orEmpty(), verified)
            }
        }
        checkOutcomeChanges(changes, updatingTestData, recordingOutcomes)
    }

    private fun FirFunction.span(): FunctionSpan? {
        val source = source?.takeIf { it.kind is KtRealSourceElementKind } ?: return null
        val name = when (this) {
            is FirSimpleFunction -> name.asString()
            is FirPropertyAccessor -> "${if (isGetter) "get" else "set"} ${propertySymbol.name}"
            is FirConstructor -> "constructor"
            else -> return null
        }
        return FunctionSpan(name, source.startOffset, source.endOffset)
    }
}

val TestServices.verificationOutcomes: VerificationOutcomes by TestServices.testServiceAccessor()

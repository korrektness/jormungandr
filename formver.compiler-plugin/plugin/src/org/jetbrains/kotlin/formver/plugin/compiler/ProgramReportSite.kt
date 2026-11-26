package org.jetbrains.kotlin.formver.plugin.compiler

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.KtFakeSourceElementKind
import org.jetbrains.kotlin.KtPsiSourceElement
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.declarations.FirAnonymousFunction
import org.jetbrains.kotlin.fir.declarations.FirConstructor
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirDeclarationOrigin
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.declarations.FirPropertyAccessor
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.formver.common.DeclarationReport
import org.jetbrains.kotlin.formver.common.PluginConfiguration
import org.jetbrains.kotlin.formver.common.source.ProofSourceIndex
import org.jetbrains.kotlin.formver.common.source.RegionKind
import org.jetbrains.kotlin.formver.core.conversion.ContractLambda
import org.jetbrains.kotlin.formver.core.conversion.EmittedCallableEvidence
import org.jetbrains.kotlin.formver.core.conversion.InheritedImplementationObligation
import org.jetbrains.kotlin.formver.core.conversion.contractLambdaCandidates
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtProperty
import java.io.IOException

/** Carries the same reporting identity from conversion to terminal result replay. */
internal class ProgramReportSite(
    private val report: DeclarationReport,
    private val row: DeclarationReport.Row,
    private val regions: Map<FirDeclaration, String> = emptyMap(),
    private val contracts: Map<ContractLambda, String> = emptyMap(),
    private val initializers: ConstructorReportLinks? = null,
) {
    val tracksBodies: Boolean get() = regions.isNotEmpty()
    val tracksConstruction: Boolean get() = initializers?.isEmpty == false

    fun withEmittedEvidence(
        evidence: EmittedCallableEvidence?,
        construction: Map<FirConstructor, Set<FirDeclaration>> = emptyMap(),
    ): ProgramReportSite {
        val emitted = evidence?.bodies.orEmpty().toSet()
        val links = regions.entries.groupBy({ it.value }, { it.key })
            .filterValues { targets -> targets.all { it in emitted } }.keys.sorted()
        val emittedSpecs = evidence?.contractLambdas.orEmpty()
        val specs = contracts.entries.groupBy({ it.value }, { it.key })
            .filterValues { targets -> targets.all { it in emittedSpecs } }.keys.sorted()
        return ProgramReportSite(report, row.copy(
            directBodyRegionIds = links.joinToString(","),
            specOnlyLambdas = specs.joinToString(","),
            enclosingConstructorLinks = initializers?.emitted(construction).orEmpty(),
        ))
    }

    fun record(outcome: DeclarationReport.Row.() -> DeclarationReport.Row) {
        try {
            report.write(row.outcome())
        } catch (error: IOException) {
            throw ReportAppendException(error)
        }
    }
}

internal class ReportAppendException(cause: IOException) : IOException(cause)

context(context: CheckerContext)
@OptIn(SymbolInternals::class)
internal fun PluginConfiguration.programReportSite(
    owner: FirDeclaration,
    index: Int = 0,
    target: FirDeclaration = owner,
    inherited: List<InheritedImplementationObligation> = emptyList(),
    constructors: List<FirConstructorSymbol> = emptyList(),
): ProgramReportSite? {
    val report = declarationReport ?: return null
    if (target is FirFunction && target.body == null) return null
    val ownerLabel = owner.verificationLabel
    val path = checkNotNull(context.containingFilePath) { "Program owner $ownerLabel has no containing file path" }
    val program = when {
        index > 0 -> "lambda${index - 1}"
        owner is FirRegularClass -> "class"
        else -> "body"
    }
    val ownerSource = checkNotNull(owner.source) { "Program owner $ownerLabel has no source location in $path" }
    val lineMapping = checkNotNull(context.containingFileSymbol?.fir?.sourceFileLinesMapping) {
        "Program owner $ownerLabel has no source line mapping for $path"
    }
    val line = lineMapping.getLineByOffset(ownerSource.startOffset) + 1
    val row = DeclarationReport.Row(path, line, ownerLabel, program, conversion = "converted")
    val sourceIndex = report.sourceIndex ?: return ProgramReportSite(report, row)
    val (programId, regionId, syntheticReason) = sourceIndex.programIdentity(target, "$ownerLabel ($program)", path)
    val candidates = inherited.mapNotNull { obligation ->
        val implementation = obligation.implementation.fir
        val psi = implementation.psiOwner()
        if (psi == null) {
            check(implementation.origin != FirDeclarationOrigin.Source) {
                "Inherited implementation ${implementation.verificationLabel} for $ownerLabel in $path has no compiler PSI"
            }
            return@mapNotNull null
        }
        val file = psi.containingFile as KtFile
        if (externalSpecs.isExternalSpec(file.virtualFilePath)) return@mapNotNull null
        implementation to sourceIndex.file(file).requireRegion(implementation.verificationLabel, psi, RegionKind.FUNCTION)
    }.toMap()
    val regions = buildMap<FirDeclaration, String> {
        if (regionId != null) put(target, regionId)
        putAll(candidates)
    }
    val contracts = sourceIndex.contractLambdas(regions.keys.filterIsInstance<FirFunction>())
    val initializers = constructorReportLinks(sourceIndex, programId, constructors, context.session)
    return ProgramReportSite(report, row.copy(
        programId = programId,
        targetRegionId = regionId.orEmpty(),
        inheritedCandidateRegionIds = candidates.values.distinct().sorted().joinToString(","),
        syntheticProgramReason = syntheticReason,
        contractLambdaCandidates = contracts.values.distinct().sorted().joinToString(","),
        constructorInitializerCandidates = initializers.candidates,
    ), regions, contracts, initializers)
}

private fun ProofSourceIndex.contractLambdas(functions: List<FirFunction>): Map<ContractLambda, String> = buildMap {
    for (function in functions) {
        for (lambda in function.contractLambdaCandidates()) {
            val psi = lambda.declaration.psiOwner() ?: continue
            val region = file(psi.containingFile as KtFile).region(psi, RegionKind.LAMBDA) ?: continue
            put(lambda, "$region:${lambda.reason.name}")
        }
    }
}

private fun FirDeclaration.psiOwner() = (source as? KtPsiSourceElement)?.psi?.let {
    if (it is KtFunctionLiteral) it.parent else it
}

private data class ProgramIdentity(
    val programId: String,
    val regionId: String? = null,
    val syntheticReason: DeclarationReport.SyntheticProgramReason? = null,
)

@OptIn(SymbolInternals::class)
private fun ProofSourceIndex.programIdentity(target: FirDeclaration, label: String, path: String): ProgramIdentity {
    val owner = checkNotNull(target.psiOwner()) {
        "Program target $label has no compiler PSI in $path (source offset: ${target.source?.startOffset})"
    }
    val file = file(owner.containingFile as KtFile)
    if (target is FirRegularClass) {
        check(owner is KtClassOrObject) {
            "No class syntax for $label at ${file.source.path}:${owner.textOffset}"
        }
        val span = owner.textRange
        return ProgramIdentity(
            file.source.stableId("class-program-v1", "${span.startOffset}:${span.endOffset}"),
            syntheticReason = DeclarationReport.SyntheticProgramReason.CLASS_OBLIGATION_BUNDLE,
        )
    }
    val kind = when (target) {
        is FirAnonymousFunction -> if (target.isLambda) RegionKind.LAMBDA else RegionKind.FUNCTION
        is FirPropertyAccessor -> if (target.isGetter) RegionKind.GETTER else RegionKind.SETTER
        else -> RegionKind.FUNCTION
    }
    if (target is FirPropertyAccessor && target.source?.kind == KtFakeSourceElementKind.DelegatedPropertyAccessor) {
        val property = checkNotNull(target.propertySymbol.fir.psiOwner() as? KtProperty) {
            "No property syntax for $label at ${file.source.path}:${owner.textOffset}"
        }
        val delegate = file.requireRegion(label, property, RegionKind.PROPERTY_DELEGATE)
        return ProgramIdentity(
            file.source.stableId("delegated-accessor-program-v1", "$kind:$delegate"),
            syntheticReason = DeclarationReport.SyntheticProgramReason.DELEGATED_ACCESSOR,
        )
    }
    val region = file.requireRegion(label, owner, kind)
    return ProgramIdentity(file.source.stableId("callable-program-v1", region), region)
}

private fun ProofSourceIndex.IndexedFile.requireRegion(label: String, owner: PsiElement, kind: RegionKind) =
    checkNotNull(region(owner, kind)) {
        "No unique $kind census identity for $label at ${source.path}:${owner.textOffset}"
    }

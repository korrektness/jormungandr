package org.jetbrains.kotlin.formver.plugin.compiler

import org.jetbrains.kotlin.KtPsiSourceElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.DirectDeclarationsAccess
import org.jetbrains.kotlin.fir.declarations.FirAnonymousInitializer
import org.jetbrains.kotlin.fir.declarations.FirConstructor
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirDeclarationOrigin
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.resolve.toRegularClassSymbol
import org.jetbrains.kotlin.fir.references.toResolvedConstructorSymbol
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.formver.common.PluginConfiguration
import org.jetbrains.kotlin.formver.common.source.ProofSourceIndex
import org.jetbrains.kotlin.formver.common.source.RegionKind
import org.jetbrains.kotlin.psi.KtAnonymousInitializer
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtSecondaryConstructor

/** Expected initializer obligations and their exact FIR identities, scoped to one program row. */
internal class ConstructorReportLinks(private val pairs: Map<Pair<FirConstructor, FirDeclaration>, String>) {
    val candidates: String get() = pairs.values.distinct().sorted().joinToString(",")
    val isEmpty: Boolean get() = pairs.isEmpty()

    fun emitted(bodies: Map<FirConstructor, Set<FirDeclaration>>): String = pairs.entries.groupBy({ it.value }, { it.key })
        .filterValues { sources -> sources.all { (constructor, initializer) -> initializer in bodies[constructor].orEmpty() } }
        .keys.sorted().joinToString(",")
}

/** Enumerates candidates from FIR constructor delegation and declaration slots, independently of emitted bodies. */
@OptIn(SymbolInternals::class, DirectDeclarationsAccess::class)
internal fun PluginConfiguration.constructorReportLinks(
    sourceIndex: ProofSourceIndex,
    programId: String,
    constructors: List<FirConstructorSymbol>,
    session: FirSession,
): ConstructorReportLinks = ConstructorReportLinks(buildMap {
    for (root in constructors) {
        val constructor = root.fir
        val owner = checkNotNull(root.resolvedReturnType.toRegularClassSymbol(session))
        val classPsi = checkNotNull((owner.source as? KtPsiSourceElement)?.psi) {
            "No compiler PSI for constructor owner ${owner.classId} in program $programId"
        }
        val file = sourceIndex.file(classPsi.containingFile as KtFile)
        val spelling = if (root.isPrimary) "primary" else {
            val psi = checkNotNull((root.source as? KtPsiSourceElement)?.psi as? KtSecondaryConstructor) {
                "No secondary constructor syntax for ${owner.classId} at ${file.source.path}:${root.source?.startOffset}"
            }
            "secondary:${psi.textRange.startOffset}:${psi.textRange.endOffset}"
        }
        val obligation = file.source.stableId("constructor-obligation-v1", "$programId:$spelling")
        val chain = generateSequence(root) {
            it.fir.delegatedConstructor?.calleeReference?.toResolvedConstructorSymbol()?.takeIf { parent ->
                parent.origin == FirDeclarationOrigin.Source
            }
        }
        val declaringClasses = chain.map { checkNotNull(it.resolvedReturnType.toRegularClassSymbol(session)) }.distinct()
        for (declaring in declaringClasses) {
            for (declaration in declaring.fir.declarations) {
                val region = initializerRegion(sourceIndex, declaration) ?: continue
                put(constructor to declaration, "$region:$obligation")
            }
        }
    }
})

private fun PluginConfiguration.initializerRegion(sourceIndex: ProofSourceIndex, declaration: FirDeclaration): String? {
    val psi = (declaration.source as? KtPsiSourceElement)?.psi ?: return null
    val kind = when {
        declaration is FirAnonymousInitializer && psi is KtAnonymousInitializer -> RegionKind.INIT_BLOCK
        declaration is FirProperty && psi is KtProperty && psi.initializer != null -> RegionKind.PROPERTY_INITIALIZER
        else -> return null
    }
    val file = psi.containingFile as KtFile
    if (externalSpecs.isExternalSpec(file.virtualFilePath)) return null
    return sourceIndex.file(file).region(psi, kind)
}

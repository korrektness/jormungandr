package org.jetbrains.kotlin.formver.common.source

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtFile
import java.nio.file.Files
import java.nio.file.Path
import java.util.IdentityHashMap

/** Resolves reporting targets to census identities using the same syntax owners as the independent census. */
class ProofSourceIndex(private val compilation: String, checkout: Path) {
    init {
        requireCensusCompilationId(compilation)
    }

    private val root = checkout.toRealPath().also {
        require(Files.isDirectory(it)) { "Proof checkout is not a directory: $it" }
    }
    private val files = IdentityHashMap<KtFile, IndexedFile>()

    @Synchronized
    fun file(file: KtFile): IndexedFile = files.getOrPut(file) {
        val source = SourceIdentity.fromParsedFile(compilation, root, Path.of(file.virtualFilePath), file.node.text)
        IndexedFile(source, sourceRegions(file).groupBy { it.owner })
    }

    class IndexedFile internal constructor(
        val source: SourceIdentity,
        private val regions: Map<PsiElement, List<SourceRegion>>,
    ) {
        fun region(owner: PsiElement, kind: RegionKind): String? = regions[owner]
            ?.singleOrNull { it.id.span.kind == kind }?.let { source.regionId(it.id) }
    }
}

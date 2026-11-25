package org.jetbrains.kotlin.formver.cli

import org.jetbrains.kotlin.KtPsiSourceElement
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.DeclarationCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirFileChecker
import org.jetbrains.kotlin.fir.analysis.extensions.FirAdditionalCheckersExtension
import org.jetbrains.kotlin.fir.declarations.FirFile
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar
import org.jetbrains.kotlin.formver.common.ExternalSpecSources
import org.jetbrains.kotlin.psi.KtFile
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.security.MessageDigest

/** Records the disk bytes and parsed syntax text of each PSI-backed FIR file, independently of proof selection. */
internal class CompilerInputManifest(private val output: File, private val externalSpecs: ExternalSpecSources) : FirExtensionRegistrar() {
    init {
        Files.writeString(output.toPath(), "compiler-inputs-v1\n", StandardOpenOption.CREATE_NEW)
    }

    override fun ExtensionRegistrarContext.configurePlugin() {
        +{ session: FirSession ->
            object : FirAdditionalCheckersExtension(session) {
                override val declarationCheckers = object : DeclarationCheckers() {
                    override val fileCheckers = setOf(object : FirFileChecker(MppCheckerKind.Common) {
                        context(context: CheckerContext, reporter: DiagnosticReporter)
                        override fun check(declaration: FirFile) {
                            record(declaration)
                        }
                    })
                }
            }
        }
    }

    @Synchronized
    private fun record(file: FirFile) {
        val source = checkNotNull(file.sourceFile) { "FIR file has no backing input: ${file.name}" }
        val path = File(checkNotNull(source.path) { "FIR source has no path: ${file.name}" }).canonicalPath
        check(path.none { it == '\t' || it == '\n' || it == '\r' }) { "Invalid manifest path: $path" }
        val kind = if (externalSpecs.isExternalSpec(source)) "spec" else "source"
        val rawDigest = File(path).readBytes().sha256()
        val parsed = (file.source as? KtPsiSourceElement)?.psi as? KtFile
        checkNotNull(parsed) { "FIR input has no parsed PSI: $path" }
        val parsedDigest = parsed.node.text.toByteArray().sha256()
        output.appendText("$kind\t$path\t$rawDigest\t$parsedDigest\n")
    }

    private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256").digest(this).toHexString()
}

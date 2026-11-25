package org.jetbrains.kotlin.formver.census

import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.psi.KtPsiFactory
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlin.io.path.Path
import kotlin.io.path.createTempFile
import kotlin.io.path.name

private const val ARGUMENT_COUNT = 3
private const val SOURCE_COLUMN_COUNT = 4
private val compilationId = Regex("(?::[A-Za-z0-9_-][A-Za-z0-9_.-]*)*:compileKotlin")
private val sha256 = Regex("[0-9a-f]{64}")

fun main(args: Array<String>) {
    require(args.size == ARGUMENT_COUNT) { "Expected checkout, census inputs and output paths; received ${args.size} arguments" }
    writeSourceCensus(Path(args[0]), Path(args[1]), Path(args[2]))
}

/** Writes a complete census from reconciled input identities and the exact bytes they identify. */
fun writeSourceCensus(checkout: Path, manifest: Path, output: Path) {
    val root = checkout.toRealPath()
    val sources = readSources(manifest)
    val disposable = Disposer.newDisposable()
    try {
        val environment = KotlinCoreEnvironment.createForProduction(
            disposable, CompilerConfiguration(), EnvironmentConfigFiles.JVM_CONFIG_FILES,
        )
        val factory = KtPsiFactory(environment.project, false)
        val pending = createTempFile(output.toAbsolutePath().parent, prefix = ".source-census-")
        try {
            Files.newBufferedWriter(pending).use { writer ->
                writer.appendLine("compilation\troot\tpath\tsha256\tkind\tstart\tend\tdiscriminator\tparent")
                for (source in sources) inventoryFile(root, source, factory, writer)
            }
            Files.move(pending, output, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(pending)
        }
    } finally {
        Disposer.dispose(disposable)
    }
}

private data class Source(val compilation: String, val root: String, val path: String, val digest: String) {
    fun columns(): String = "$compilation\t$root\t$path\t$digest"
}

private fun readSources(manifest: Path): List<Source> {
    val contents = Files.readString(manifest)
    require(contents.startsWith("census-inputs-v1\n") && contents.endsWith('\n') && '\r' !in contents) {
        "Malformed census input manifest: $manifest"
    }
    val fields = contents.dropLast(1).split('\n').drop(1)
    val sources = mutableMapOf<Triple<String, String, String>, Source>()
    for (row in fields) {
        val columns = row.split('\t')
        require(columns.size == SOURCE_COLUMN_COUNT) { "Malformed census input row in $manifest: $row" }
        val (compilation, root, relative, digest) = columns
        val path = Path(relative)
        require(compilation.matches(compilationId) && root == "checkout" &&
            relative.isNotEmpty() && !path.isAbsolute && !path.startsWith("..") && path.normalize().toString() == relative &&
            digest.matches(sha256)) {
            "Invalid census input identity in $manifest: $row"
        }
        val key = Triple(compilation, root, relative)
        require(sources.put(key, Source(compilation, root, relative, digest)) == null) {
            "Duplicate census input in $manifest: $key"
        }
    }
    return sources.values.sortedWith(compareBy(Source::compilation, Source::root, Source::path))
}

private fun inventoryFile(root: Path, source: Source, factory: KtPsiFactory, output: Appendable) {
    val path = root.resolve(source.path)
    val actual = path.toRealPath()
    require(actual == path) { "Census input path $path resolves to $actual" }
    val bytes = Files.readAllBytes(path)
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes).toHexString()
    require(digest == source.digest) { "Census input $path has digest $digest; reconciled digest is ${source.digest}" }
    val text = Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString()
        .removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
    val file = factory.createFile(path.name, text)
    require(file.node.text == text) { "Census PSI text differs from normalized input: $path" }
    val error = PsiTreeUtil.findChildOfType(file, PsiErrorElement::class.java)
    require(error == null) { "$path:${error?.textOffset}: ${error?.errorDescription}" }
    fun write(id: RegionId, parent: RegionId?) {
        val (span, discriminator) = id
        output.appendLine("${source.columns()}\t${span.kind}\t${span.start}\t${span.end}\t$discriminator\t${parent?.reference().orEmpty()}")
    }
    val fileId = RegionId(RegionSpan(RegionKind.FILE, 0, text.length), 0)
    write(fileId, null)
    for ((id, parent) in sourceRegions(file)) write(id, parent ?: fileId)
}

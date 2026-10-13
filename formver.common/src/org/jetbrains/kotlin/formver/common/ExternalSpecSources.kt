/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.common

import org.jetbrains.kotlin.KtSourceFile
import org.jetbrains.kotlin.fir.declarations.FirFile
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Kotlin source files that the compiler analyses as specifications of code outside the module.
 *
 * Their declarations take part in resolution and conversion, but the compiled output contains none of them, so
 * ordinary module code may not refer to them.
 */
class ExternalSpecSources(files: Collection<File>) {
    /** The spec files, canonical and without duplicates, in the order given. */
    val files: List<File> = files.map { it.canonicalFile }.distinct()

    private val canonicalPaths: Set<String> = this.files.mapTo(HashSet()) { it.path }
    private val pathMembership = ConcurrentHashMap<String, Boolean>()

    fun isEmpty(): Boolean = files.isEmpty()

    /**
     * Whether [path], a source path as the compiler reports it, names one of the spec files. A file without a path is
     * never one: spec files are loaded from disk.
     */
    fun isExternalSpec(path: String?): Boolean {
        if (path == null || canonicalPaths.isEmpty()) return false
        return pathMembership.computeIfAbsent(path) { File(it).canonicalPath in canonicalPaths }
    }

    fun isExternalSpec(file: KtSourceFile?): Boolean = isExternalSpec(file?.path)

    fun isExternalSpec(file: FirFile): Boolean = isExternalSpec(file.sourceFile)

    companion object {
        val NONE = ExternalSpecSources(emptyList())

        /**
         * Lists the resource paths of bundled spec sources, one per line. Every copy of this resource on the class
         * path contributes, so a jar can carry specs by shipping its own index next to them.
         */
        const val BUNDLED_INDEX_RESOURCE = "META-INF/formver/external-specs.list"

        /**
         * The spec sources bundled as resources of [classLoader], written out under [cacheRoot] so that the
         * compiler can read them as ordinary files.
         *
         * The files go into a directory named after a digest of their names and contents, so concurrent and repeated
         * compilations share one copy and a changed resource never reuses a stale one.
         */
        fun extractBundled(classLoader: ClassLoader, cacheRoot: File = defaultCacheRoot()): List<File> {
            val resources = classLoader.getResources(BUNDLED_INDEX_RESOURCE).toList()
                .flatMap { index -> index.readText().lineSequence().map(String::trim).filter(::isEntry).toList() }
                .distinct()
                .map { name ->
                    require(name.endsWith(".kt")) { "Bundled external spec $name is not a Kotlin source." }
                    val stream = classLoader.getResourceAsStream(name)
                        ?: error("Bundled external spec $name is listed in $BUNDLED_INDEX_RESOURCE but missing.")
                    name to stream.use { it.readBytes() }
                }
            if (resources.isEmpty()) return emptyList()

            val digest = MessageDigest.getInstance("SHA-256")
            for ((name, bytes) in resources) {
                digest.update(name.toByteArray())
                digest.update(0)
                digest.update(bytes)
                digest.update(0)
            }
            val dir = cacheRoot.resolve(digest.digest().joinToString("") { "%02x".format(it) })
            return resources.map { (name, bytes) -> writeOnce(dir.resolve(name), bytes) }
        }

        private fun isEntry(line: String): Boolean = line.isNotEmpty() && !line.startsWith("#")

        private fun defaultCacheRoot(): File = File(System.getProperty("java.io.tmpdir"), "formver-external-specs")

        private fun writeOnce(target: File, bytes: ByteArray): File {
            if (target.isFile) return target
            target.parentFile.mkdirs()
            val partial = Files.createTempFile(target.parentFile.toPath(), target.name, ".partial")
            Files.write(partial, bytes)
            Files.move(partial, target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            return target
        }
    }
}

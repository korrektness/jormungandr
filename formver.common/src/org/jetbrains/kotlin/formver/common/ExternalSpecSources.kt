/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.common

import org.jetbrains.kotlin.KtSourceFile
import org.jetbrains.kotlin.fir.declarations.FirFile
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipFile

/**
 * Kotlin source files that the compiler analyses as specifications of code outside the module.
 *
 * Their declarations take part in resolution and conversion, but the compiled output contains none of them, so
 * ordinary module code may not refer to them.
 */
class ExternalSpecSources(files: Collection<File>, bundled: Collection<File> = emptyList()) {
    /** The spec files, canonical and without duplicates, in the order given. */
    val files: List<File> = (files + bundled).map { it.canonicalFile }.distinct()

    private val canonicalPaths: Set<String> = this.files.mapTo(HashSet()) { it.path }
    private val bundledPaths: Set<String> = bundled.mapTo(HashSet()) { it.canonicalPath }
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

    /** Whether [path], a spec file's path, names one that SnaKt bundles rather than one the user supplied. */
    fun isBundled(path: String): Boolean = bundledPaths.isNotEmpty() && File(path).canonicalPath in bundledPaths

    fun isExternalSpec(file: FirFile): Boolean = isExternalSpec(file.sourceFile)

    companion object {
        val NONE = ExternalSpecSources(emptyList())

        /** Lists the resource paths of bundled spec sources, one per line. */
        const val BUNDLED_INDEX_RESOURCE = "META-INF/formver/external-specs.list"

        /**
         * The spec sources bundled in [location], a jar or class-path directory that carries
         * [BUNDLED_INDEX_RESOURCE], written out under [cacheRoot] so that the compiler can read them as ordinary files.
         * Only [location] is read, so other entries of the class path cannot add or replace bundled specs.
         *
         * The files go into a directory named after a digest of their names and contents, so concurrent and repeated
         * compilations share one copy and a changed resource never reuses a stale one. An existing copy whose
         * contents differ from the resource is overwritten.
         */
        fun extractBundled(location: File, cacheRoot: File = defaultCacheRoot()): List<File> {
            val resources = readBundled(location)
            if (resources.isEmpty()) return emptyList()

            val digest = MessageDigest.getInstance("SHA-256")
            for ((name, bytes) in resources) {
                digest.update(name.toByteArray())
                digest.update(0)
                digest.update(bytes)
                digest.update(0)
            }
            val dir = cacheRoot.resolve(digest.digest().joinToString("") { "%02x".format(it) })
            return resources.map { (name, bytes) -> writeChecked(dir.resolve(name), bytes) }
        }

        private fun readBundled(location: File): List<Pair<String, ByteArray>> {
            if (location.isDirectory) {
                return readIndexed(location) { name -> location.resolve(name).takeIf { it.isFile }?.readBytes() }
            }
            return ZipFile(location).use { zip ->
                readIndexed(location) { name -> zip.getEntry(name)?.let { entry -> zip.getInputStream(entry).use { it.readBytes() } } }
            }
        }

        private fun readIndexed(location: File, read: (String) -> ByteArray?): List<Pair<String, ByteArray>> {
            val index = read(BUNDLED_INDEX_RESOURCE) ?: error("$location carries no $BUNDLED_INDEX_RESOURCE.")
            return index.decodeToString().lineSequence().map(String::trim).filter(::isEntry).distinct()
                .map { name ->
                    require(name.endsWith(".kt")) { "Bundled external spec $name is not a Kotlin source." }
                    require(name.split('/').none { it.isEmpty() || it == "." || it == ".." }) {
                        "Bundled external spec $name is not a plain relative path."
                    }
                    name to (read(name) ?: error("Bundled external spec $name is listed in $BUNDLED_INDEX_RESOURCE but missing."))
                }
                .toList()
        }

        private fun isEntry(line: String): Boolean = line.isNotEmpty() && !line.startsWith("#")

        /** A directory in the user's own cache: a shared temporary directory would let other users plant specs. */
        private fun defaultCacheRoot(): File {
            val cache = System.getenv("XDG_CACHE_HOME")?.takeIf { it.isNotEmpty() }?.let(::File)
                ?: File(System.getProperty("user.home"), ".cache")
            return cache.resolve("snakt/external-specs")
        }

        private fun writeChecked(target: File, bytes: ByteArray): File {
            if (target.isFile && target.readBytes().contentEquals(bytes)) return target
            createPrivateDirectories(target.parentFile.toPath())
            val partial = Files.createTempFile(target.parentFile.toPath(), target.name, ".partial")
            Files.write(partial, bytes)
            Files.move(partial, target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            return target
        }

        private fun createPrivateDirectories(dir: Path) {
            if ("posix" in FileSystems.getDefault().supportedFileAttributeViews()) {
                val privatePermissions = PosixFilePermissions.fromString("rwx------")
                Files.createDirectories(dir, PosixFilePermissions.asFileAttribute(privatePermissions))
                Files.setPosixFilePermissions(dir, privatePermissions)
                check(Files.getPosixFilePermissions(dir) == privatePermissions) {
                    "External spec cache directory permissions are not private: $dir"
                }
            } else {
                Files.createDirectories(dir)
            }
        }
    }
}

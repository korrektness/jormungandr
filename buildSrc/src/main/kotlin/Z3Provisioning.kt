import org.gradle.api.GradleException
import java.io.File
import java.net.URI
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 * Provides the Z3 binary that Silicon runs during verification tests: the one `Z3_EXE` names when that is an
 * absolute path to a regular file, otherwise the pinned official release, downloaded once and cached under the
 * Gradle user home.
 */
object Z3Provisioning {
    private const val VERSION = "4.8.7"

    private class Release(val name: String, val sha256: String, val exe: String)

    private fun release(): Release {
        val os = System.getProperty("os.name")
        val arch = System.getProperty("os.arch")
        // macOS on arm64 runs the x64 build under Rosetta; 4.8.7 has no arm64 release.
        if (arch != "amd64" && arch != "x86_64" && !(os.startsWith("Mac") && arch == "aarch64")) {
            throw GradleException("No Z3 $VERSION release for $os/$arch.")
        }
        return when {
            os.startsWith("Linux") ->
                Release("z3-$VERSION-x64-ubuntu-16.04", "fcde3273ba88e291fe93db4b9d39957274700caeebba8aefbae28796da0dc0b7", "z3")
            os.startsWith("Mac") ->
                Release("z3-$VERSION-x64-osx-10.14.6", "49fa41210ff572ae56476befafbeb4a82bbf921f843daf73ef5451f7bcd6d2c5", "z3")
            os.startsWith("Windows") ->
                Release("z3-$VERSION-x64-win", "780874697a7aef2028d7b8bc31e78be760622e0c3823ed1731a6ee7072fa60cd", "z3.exe")
            else -> throw GradleException("No Z3 $VERSION release for $os.")
        }
    }

    /** Returns the absolute path of the Z3 binary, downloading the pinned release into [gradleUserHome] if needed. */
    fun z3Exe(gradleUserHome: File, z3ExeEnv: String?): File {
        z3ExeEnv?.let(::File)?.takeIf { it.isAbsolute && it.isFile }?.let { return it }

        val release = release()
        val installDir = gradleUserHome.resolve("formver/z3/${release.name}")
        val exe = installDir.resolve("bin/${release.exe}")
        if (exe.isFile) return exe

        val parent = installDir.parentFile.apply { mkdirs() }
        val tmpDir = Files.createTempDirectory(parent.toPath(), "${release.name}-").toFile()
        try {
            val zip = tmpDir.resolve("${release.name}.zip")
            val url = "https://github.com/Z3Prover/z3/releases/download/z3-$VERSION/${release.name}.zip"
            URI(url).toURL().openStream().use { input -> zip.outputStream().use { input.copyTo(it) } }
            val actual = MessageDigest.getInstance("SHA-256").digest(zip.readBytes()).joinToString("") { "%02x".format(it) }
            if (actual != release.sha256) {
                throw GradleException("Checksum mismatch for $url: expected ${release.sha256}, got $actual.")
            }
            val extracted = tmpDir.resolve("extracted")
            unzip(zip, extracted)
            val extractedExe = extracted.resolve("${release.name}/bin/${release.exe}")
            if (!extractedExe.setExecutable(true)) throw GradleException("Cannot make $extractedExe executable.")
            try {
                Files.move(extracted.resolve(release.name).toPath(), installDir.toPath(), StandardCopyOption.ATOMIC_MOVE)
            } catch (e: FileSystemException) {
                // The move fails when a concurrent build installed the same release first; that install is used.
                if (!exe.isFile) throw e
            }
        } finally {
            tmpDir.deleteRecursively()
        }
        return exe
    }

    private fun unzip(zip: File, target: File) {
        val root = target.canonicalFile
        ZipInputStream(zip.inputStream().buffered()).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                val out = root.resolve(entry.name).canonicalFile
                if (!out.startsWith(root)) throw GradleException("Zip entry ${entry.name} escapes $root.")
                if (entry.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile.mkdirs()
                    out.outputStream().use { input.copyTo(it) }
                }
            }
        }
    }
}

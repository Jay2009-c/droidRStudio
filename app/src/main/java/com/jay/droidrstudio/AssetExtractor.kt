package com.jay.droidrstudio

import android.content.Context
import android.os.Build
import android.system.Os
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.util.zip.GZIPInputStream

sealed class ExtractionState {
    object Idle : ExtractionState()
    data class Extracting(val progress: Float) : ExtractionState()
    object Success : ExtractionState()
    data class Error(val message: String) : ExtractionState()
}

class AssetExtractor(private val context: Context) {
    companion object {
        private const val TAG = "AssetExtractor"
        private const val ROOTFS_DIRECTORY = "rootfs"
        // Try both names in case AGP decompresses the asset during build.
        private val ARCHIVE_ASSET_NAMES = listOf("alpine_r.tar", "alpine_r.tar.gz")
        private const val BUFFER_SIZE = 128 * 1024 // 128KB
    }

    private val _state = MutableStateFlow<ExtractionState>(ExtractionState.Idle)
    val state: StateFlow<ExtractionState> = _state.asStateFlow()

    suspend fun extractAsset(targetDir: File) = withContext(Dispatchers.IO) {
        var lastError: Exception? = null
        
        for (assetName in ARCHIVE_ASSET_NAMES) {
            try {
                Log.d(TAG, "Attempting to extract $assetName to ${targetDir.absolutePath}")
                performExtraction(assetName, targetDir)
                return@withContext // Success
            } catch (e: Exception) {
                lastError = e
                Log.w(TAG, "Failed to extract $assetName: ${e.message}")
            }
        }
        
        val errorMsg = lastError?.let { "${it.javaClass.simpleName}: ${it.message}" } ?: "Unknown error"
        _state.value = ExtractionState.Error(errorMsg)
        targetDir.deleteRecursively()
    }

    private fun performExtraction(assetName: String, targetDir: File) {
        _state.value = ExtractionState.Extracting(0f)
        if (targetDir.exists()) {
            targetDir.deleteRecursively()
        }
        if (!targetDir.mkdirs()) {
            throw Exception("Failed to create target directory: ${targetDir.absolutePath}")
        }

        val assetManager = context.assets
        val totalSize = try {
            assetManager.openFd(assetName).use { it.length }
        } catch (_: Exception) {
            -1L
        }

        var bytesReadFromSource: Long = 0

        assetManager.open(assetName).use { rawInputStream ->
            val bufferedInput = BufferedInputStream(rawInputStream, BUFFER_SIZE)

            // Peek magic bytes to verify if it's gzipped
            bufferedInput.mark(2)
            val magic1 = bufferedInput.read()
            val magic2 = bufferedInput.read()
            bufferedInput.reset()

            val isGzip = magic1 == 0x1f && magic2 == 0x8b
            Log.d(TAG, "Asset $assetName detected as ${if (isGzip) "GZIP" else "raw TAR"}")

            val progressStream = progressInput(bufferedInput) { bytesRead ->
                bytesReadFromSource += bytesRead
                updateProgress(bytesReadFromSource, totalSize)
            }

            val baseStream: InputStream = if (isGzip) {
                GZIPInputStream(progressStream)
            } else {
                progressStream
            }

            baseStream.use { decodedStream ->
                TarArchiveInputStream(decodedStream).use { tarStream ->
                    var entry = tarStream.nextEntry as? TarArchiveEntry
                    var extractedEntries = 0
                    while (entry != null) {
                        extractedEntries++
                        val destFile = destinationFile(targetDir, entry.name)
                        if (entry.isDirectory) {
                            if (!destFile.exists() && !destFile.mkdirs()) {
                                throw Exception("Failed to create directory: ${destFile.absolutePath}")
                            }
                        } else if (entry.isSymbolicLink) {
                            createSymbolicLink(entry.linkName, destFile, targetDir)
                        } else {
                            destFile.parentFile?.mkdirs()
                            BufferedOutputStream(FileOutputStream(destFile), BUFFER_SIZE).use { output ->
                                tarStream.copyTo(output)
                            }
                            if ((entry.mode and 73) != 0) {
                                destFile.setExecutable(true, false)
                            }
                        }
                        entry = tarStream.nextEntry as? TarArchiveEntry
                    }
                    check(extractedEntries > 0) {
                        "Archive contains no entries: $assetName"
                    }
                }
            }
        }
        Log.d(TAG, "Extraction of $assetName successful. Verifying loader...")
        verifyLoader(targetDir)
        _state.value = ExtractionState.Success
    }

    private fun verifyLoader(targetDir: File) {
        val loaderPath = File(targetDir, "rootfs/lib/ld-musl-aarch64.so.1")
        if (loaderPath.exists()) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val target = Files.readSymbolicLink(loaderPath.toPath())
                    val targetFile = File(loaderPath.parentFile, target.toString())
                    if (!targetFile.exists()) {
                        Log.e(TAG, "Loader symlink target DOES NOT exist: $target (at ${targetFile.absolutePath})")
                    } else {
                        Log.d(TAG, "Loader symlink target verified: $target")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to verify loader symlink: ${e.message}")
            }
        } else {
            Log.e(TAG, "Loader symlink itself is missing: ${loaderPath.absolutePath}")
        }
    }

    private fun updateProgress(read: Long, total: Long) {
        if (total > 0) {
            _state.value = ExtractionState.Extracting(read.toFloat() / total)
        }
    }

    private fun progressInput(
        input: InputStream,
        onBytesRead: (Long) -> Unit
    ): InputStream = object : InputStream() {
        override fun read(): Int = input.read().also { if (it != -1) onBytesRead(1) }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            input.read(buffer, offset, length).also { if (it > 0) onBytesRead(it.toLong()) }

        override fun close() = input.close()
    }

    private fun destinationFile(targetDir: File, entryName: String): File {
        val destination = File(targetDir, entryName)
        val targetPath = targetDir.canonicalPath + File.separator
        check(destination.canonicalPath.startsWith(targetPath)) {
            "Archive entry escapes the Alpine root: $entryName"
        }
        return destination
    }

    private fun createSymbolicLink(target: String, link: File, rootDir: File) {
        val adjustedTarget = if (target.startsWith("/")) {
            // Absolute symlinks in the TAR point to the root of the archive.
            // When extracted to a subdirectory on Android, we must make them relative
            // so that they remain within the rootfs when followed by proot or the kernel.
            val linkDir = link.parentFile ?: rootDir
            val archiveRoot = File(rootDir, ROOTFS_DIRECTORY)
            val targetFile = File(archiveRoot, target.removePrefix("/"))
            
            // Manual relativization to support API level < 26
            relativizePaths(linkDir, targetFile)
        } else {
            target
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val linkPath = Paths.get(link.absolutePath)
                Files.deleteIfExists(linkPath)
                Files.createSymbolicLink(linkPath, Paths.get(adjustedTarget))
                // Verify the link immediately
                val verifiedTarget = Files.readSymbolicLink(linkPath)
                Log.d(TAG, "Created symlink (O+): ${link.absolutePath} -> $verifiedTarget")
            } else {
                if (link.exists()) link.delete()
                Os.symlink(adjustedTarget, link.absolutePath)
                Log.d(TAG, "Created symlink (Pre-O): ${link.absolutePath} -> $adjustedTarget")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create symlink: ${link.absolutePath} -> $adjustedTarget", e)
            throw Exception("Failed to create symlink: ${link.absolutePath} -> $adjustedTarget: ${e.message}")
        }
    }

    /**
     * Calculates the relative path from baseDir to targetFile.
     */
    private fun relativizePaths(baseDir: File, targetFile: File): String {
        val base = baseDir.canonicalPath.split(File.separator).filter { it.isNotEmpty() }
        val target = targetFile.canonicalPath.split(File.separator).filter { it.isNotEmpty() }
        
        var common = 0
        while (common < base.size && common < target.size && base[common] == target[common]) {
            common++
        }
        
        val result = mutableListOf<String>()
        repeat(base.size - common) {
            result.add("..")
        }
        for (i in common until target.size) {
            result.add(target[i])
        }
        return if (result.isEmpty()) "." else result.joinToString(File.separator)
    }

    suspend fun copyAsset(assetName: String, targetFile: File) = withContext(Dispatchers.IO) {
        try {
            targetFile.parentFile?.mkdirs()
            context.assets.open(assetName).use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            targetFile.setExecutable(true, false)
        } catch (e: Exception) {
            val errorMsg = "${e.javaClass.simpleName}: Failed to copy asset $assetName to ${targetFile.absolutePath}: ${e.message}"
            Log.e(TAG, errorMsg, e)
            throw Exception(errorMsg)
        }
    }
}

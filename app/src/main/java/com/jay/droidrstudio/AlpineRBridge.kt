package com.jay.droidrstudio

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File

/**
 * A bridge to execute R code within an Alpine Linux environment using proot.
 */
class AlpineRBridge(private val context: Context) {
    companion object {
        private const val ROOTFS_DIRECTORY = "rootfs"
        private const val R_SCRIPT_PATH = "usr/bin/Rscript"
        private const val MUSL_LOADER_PATH = "/lib/ld-musl-aarch64.so.1"
        private const val TALLOC_ASSET = "libtalloc.so.2"
        private const val PROOT_LOADER_FILE = "libproot-loader.so"
    }

    private val _output = MutableStateFlow<List<String>>(emptyList())
    val output: StateFlow<List<String>> = _output.asStateFlow()

    private val mutex = Mutex()

    /**
     * Saves the R code to the script file.
     */
    private fun writeRCode(rCode: String, runnerFile: File) {
        check(runnerFile.parentFile!!.mkdirs() || runnerFile.parentFile!!.isDirectory)
        runnerFile.writeText(rCode)
    }

    /**
     * Ensures a sensible .Rprofile exists in the guest /root.
     */
    private fun ensureRProfile(alpineRoot: File) {
        val rProfile = File(alpineRoot, "root/.Rprofile")
        if (!rProfile.exists()) {
            rProfile.writeText("options(bitmapType='cairo')\n")
        }
    }

    /**
     * Ensures DNS is configured for Alpine inside PRoot.
     */
    private fun ensureNetworkConfig(alpineRoot: File) {
        val etcDir = File(alpineRoot, "rootfs/etc")
        if (!etcDir.exists()) etcDir.mkdirs()
        val resolvConf = File(etcDir, "resolv.conf")
        // Use Google public DNS for reliability inside the container
        resolvConf.writeText("nameserver 8.8.8.8\nnameserver 8.8.4.4\n")
    }

    /**
     * Installs a versioned native dependency that Android's jniLibs packaging
     * excludes because its filename does not end in .so.
     */
    private fun installNativeDependency(assetName: String): File {
        val nativeDependenciesDir = File(context.filesDir, "native-libs")
        val dependency = File(nativeDependenciesDir, assetName)

        if (!dependency.isFile) {
            check(nativeDependenciesDir.mkdirs() || nativeDependenciesDir.isDirectory)
            context.assets.open(assetName).use { input ->
                dependency.outputStream().use(input::copyTo)
            }
            dependency.setReadable(true, false)
        }

        return dependency
    }

    private fun installProotDependency(): File {
        return installNativeDependency(TALLOC_ASSET).parentFile!!
    }

    /**
     * Executes the R script using proot.
     */
    private suspend fun executeRScript(runnerFile: File, alpineRoot: File) = withContext(Dispatchers.IO) {
        val prootPath = File(context.applicationInfo.nativeLibraryDir, "libproot.so").canonicalPath
        val prootDependencyPath = installProotDependency().canonicalPath
        val nativeLibPath = context.applicationInfo.nativeLibraryDir
        val prootLoader = File(nativeLibPath, PROOT_LOADER_FILE)
        check(prootLoader.isFile) {
            "Missing PRoot loader: ${prootLoader.absolutePath}. " +
                "Copy Termux's \$PREFIX/libexec/proot/loader to " +
                "app/src/main/jniLibs/arm64-v8a/$PROOT_LOADER_FILE, then rebuild and reinstall the app."
        }
        val prootTempDir = File(context.cacheDir, "proot-tmp")
        check(prootTempDir.mkdirs() || prootTempDir.isDirectory) {
            "Unable to create PRoot temporary directory: ${prootTempDir.absolutePath}"
        }
        
        val canonicalAlpineRoot = alpineRoot.canonicalPath
        
        /*
         * These are host-side variables.  libproot is linked by Android's
         * linker, so it must search only the Android-compatible libtalloc
         * directory.  Alpine's musl directories must not be added here: the
         * guest loader resolves them after PRoot has switched to the rootfs.
         */
        val hostEnv = mapOf(
            "LD_LIBRARY_PATH" to "$prootDependencyPath:$nativeLibPath",
            "PROOT_TMP_DIR" to prootTempDir.absolutePath,
            "PROOT_LOADER" to prootLoader.canonicalPath
        )

        val processBuilder = ProcessBuilder(
            prootPath,
            "-0", // Fake root
            "-r", canonicalAlpineRoot,
            "-b", "/dev",
            "-b", "/proc",
            "-b", "/sys",
            "-w", "/root",
            "/usr/bin/Rscript",
            runnerFile.name
        )

        applyEnvironment(processBuilder, hostEnv)

        processBuilder.redirectErrorStream(true)
        val process = processBuilder.start()

        try {
            withTimeout(60000) {
                process.inputStream.bufferedReader().use { reader ->
                    var line: String? = reader.readLine()
                    while (line != null) {
                        val currentLine = line
                        _output.update { it + currentLine }
                        line = reader.readLine()
                    }
                }
                process.waitFor()
            }
        } finally {
            if (isAlive(process)) {
                process.destroy()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    process.destroyForcibly()
                }
            }
        }
    }

    /**
     * Returns the guest /root directory where R scripts run.
     */
    fun getGuestRoot(): File {
        return File(context.filesDir, "alpine/$ROOTFS_DIRECTORY/root")
    }

    suspend fun execute(rCode: String) {
        mutex.withLock {
            _output.value = emptyList()

            try {
                val alpineRoot = File(context.filesDir, "alpine/$ROOTFS_DIRECTORY")
                check(File(alpineRoot, R_SCRIPT_PATH).isFile) {
                    "Alpine rootfs is not ready: ${File(alpineRoot, R_SCRIPT_PATH).absolutePath} is missing"
                }
                check(File(alpineRoot, MUSL_LOADER_PATH.removePrefix("/")).isFile) {
                    "Alpine dynamic loader is missing: $MUSL_LOADER_PATH"
                }
                val runnerFile = File(alpineRoot, "root/runner.R")
                writeRCode(rCode, runnerFile)
                ensureRProfile(alpineRoot)
                ensureNetworkConfig(alpineRoot)
                executeRScript(runnerFile, alpineRoot)
            } catch (e: Exception) {
                _output.update { it + "Error: ${e.message}" }
            }
        }
    }

    private fun isAlive(process: Process): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            process.isAlive
        } else {
            try {
                process.exitValue()
                false
            } catch (e: IllegalThreadStateException) {
                true
            }
        }
    }

    /** Applies the host environment required to start PRoot itself. */
    private fun applyEnvironment(processBuilder: ProcessBuilder, hostEnv: Map<String, String>) {
        val env = processBuilder.environment()
        env.putAll(hostEnv)
        env.remove("LD_PRELOAD")
        env["PATH"] = "/usr/bin:/bin:/usr/sbin:/sbin:/usr/local/bin"
        env["HOME"] = "/root"
        env["USER"] = "root"
        env["TERM"] = "xterm"
    }
}

package com.jay.droidrstudio

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.OutputStream
import java.nio.charset.Charset

class PRootEngine(private val context: Context) {
    companion object {
        private const val TAG = "PRootEngine"
        private const val ROOTFS_DIRECTORY = "rootfs"
        private const val TALLOC_ASSET = "libtalloc.so.2"
        private const val PROOT_LOADER_FILE = "libproot-loader.so"
    }

    private val _rOutput = MutableStateFlow<List<String>>(emptyList())
    val rOutput: StateFlow<List<String>> = _rOutput.asStateFlow()

    private val _terminalOutput = MutableStateFlow<List<String>>(emptyList())
    val terminalOutput: StateFlow<List<String>> = _terminalOutput.asStateFlow()

    private val _plots = MutableStateFlow<List<File>>(emptyList())
    val plots: StateFlow<List<File>> = _plots.asStateFlow()

    private var terminalProcess: Process? = null
    private var terminalOutputStream: OutputStream? = null
    private var terminalJob: Job? = null

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Executes R code directly by piping it to Rscript via stdin.
     */
    fun runRScriptDirectly(code: String) {
        scope.launch {
            _rOutput.value = emptyList()
            try {
                val alpineRoot = File(context.filesDir, "alpine/$ROOTFS_DIRECTORY")
                val command = listOf("/usr/bin/env", "Rscript", "-")
                
                val process = startProotProcess(command, alpineRoot)
                
                // Pipe code to stdin
                process.outputStream.bufferedWriter().use { writer ->
                    writer.write(code)
                    writer.flush()
                }

                // Read output
                process.inputStream.bufferedReader().use { reader ->
                    var line: String? = reader.readLine()
                    while (line != null) {
                        val currentLine = line
                        _rOutput.update { it + currentLine }
                        line = reader.readLine()
                    }
                }
                
                process.waitFor()
                scanForPlots()
            } catch (e: Exception) {
                _rOutput.update { it + "Execution Error: ${e.message}" }
            }
        }
    }

    /**
     * Starts a persistent shell process for the interactive terminal.
     */
    fun startPersistentTerminal() {
        if (terminalProcess != null) return
        
        scope.launch {
            try {
                val alpineRoot = File(context.filesDir, "alpine/$ROOTFS_DIRECTORY")
                val command = listOf("/bin/sh")
                
                val process = startProotProcess(command, alpineRoot)
                terminalProcess = process
                terminalOutputStream = process.outputStream

                // Continuous read loop
                terminalJob = launch {
                    process.inputStream.bufferedReader().use { reader ->
                        var line: String? = reader.readLine()
                        while (line != null) {
                            val currentLine = line
                            _terminalOutput.update { it + currentLine }
                            line = reader.readLine()
                        }
                    }
                }
                
                process.waitFor()
                _terminalOutput.update { it + "[Shell Exited]" }
                terminalProcess = null
                terminalOutputStream = null
            } catch (e: Exception) {
                _terminalOutput.update { it + "Terminal Error: ${e.message}" }
            }
        }
    }

    /**
     * Sends a command to the persistent shell.
     */
    fun sendTerminalCommand(command: String) {
        scope.launch {
            try {
                terminalOutputStream?.let { 
                    it.write((command + "\n").toByteArray(Charset.defaultCharset()))
                    it.flush()
                } ?: run {
                    _terminalOutput.update { it + "Error: Shell not running. Restarting..." }
                    startPersistentTerminal()
                }
                // Small delay to allow shell to process and write files if any
                delay(500)
                scanForPlots()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send command", e)
            }
        }
    }

    /**
     * Scans the guest /root directory for generated plots and data files.
     */
    fun scanForPlots() {
        val guestRoot = getGuestRoot()
        if (guestRoot.exists() && guestRoot.isDirectory) {
            val plotFiles = guestRoot.listFiles { file ->
                val ext = file.extension.lowercase()
                file.isFile && (ext == "png" || ext == "pdf" || ext == "html" || ext == "json")
            }?.toList() ?: emptyList()
            _plots.value = plotFiles
        }
    }

    /**
     * Ensures DNS is configured for Alpine inside PRoot.
     */
    private fun ensureNetworkConfig(alpineRoot: File) {
        val etcDir = File(alpineRoot, "etc")
        if (!etcDir.exists()) etcDir.mkdirs()
        val resolvConf = File(etcDir, "resolv.conf")
        resolvConf.writeText("nameserver 8.8.8.8\nnameserver 8.8.4.4\n")
    }

    private fun startProotProcess(commandArgs: List<String>, alpineRoot: File): Process {
        ensureNetworkConfig(alpineRoot)
        
        val prootPath = File(context.applicationInfo.nativeLibraryDir, "libproot.so").canonicalPath
        val nativeLibPath = context.applicationInfo.nativeLibraryDir
        val prootDependencyPath = installNativeDependency(TALLOC_ASSET).parentFile!!.canonicalPath
        val prootLoader = File(nativeLibPath, PROOT_LOADER_FILE).canonicalPath
        val prootTempDir = File(context.cacheDir, "proot-tmp").apply { mkdirs() }.canonicalPath

        val fullArgs = mutableListOf(
            prootPath,
            "-0", // Fake root
            "--link2symlink", // Correct double-dash for hardlink emulation
            "-r", alpineRoot.canonicalPath,
            "-b", "/dev",
            "-b", "/proc",
            "-b", "/sys",
            "-w", "/root"
        )
        fullArgs.addAll(commandArgs)

        val processBuilder = ProcessBuilder(fullArgs)
        
        // Environment setup
        val env = processBuilder.environment()
        env.remove("LD_PRELOAD") 
        env["LD_LIBRARY_PATH"] = "$prootDependencyPath:$nativeLibPath"
        env["PROOT_TMP_DIR"] = prootTempDir
        env["PROOT_LOADER"] = prootLoader
        env["PROOT_NO_SECCOMP"] = "1"
        env["PATH"] = "/usr/bin:/bin:/usr/sbin:/sbin:/usr/local/bin"
        env["HOME"] = "/root"
        env["USER"] = "root"
        env["TERM"] = "xterm-256color"
        env["TMPDIR"] = "/tmp"

        processBuilder.redirectErrorStream(true)
        return processBuilder.start()
    }

    private fun installNativeDependency(assetName: String): File {
        val nativeDependenciesDir = File(context.filesDir, "native-libs")
        val dependency = File(nativeDependenciesDir, assetName)
        if (!dependency.isFile) {
            nativeDependenciesDir.mkdirs()
            context.assets.open(assetName).use { input ->
                dependency.outputStream().use(input::copyTo)
            }
            dependency.setReadable(true, false)
        }
        return dependency
    }

    /**
     * Returns the guest /root directory where R scripts run.
     */
    fun getGuestRoot(): File {
        return File(context.filesDir, "alpine/$ROOTFS_DIRECTORY/root")
    }

    fun clearROutput() {
        _rOutput.value = emptyList()
    }

    fun clearTerminalOutput() {
        _terminalOutput.value = emptyList()
    }

    fun stopAll() {
        terminalJob?.cancel()
        terminalProcess?.destroy()
        terminalProcess = null
        terminalOutputStream = null
    }
}

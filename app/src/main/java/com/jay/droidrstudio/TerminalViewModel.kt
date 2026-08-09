package com.jay.droidrstudio

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class TerminalViewModel(application: Application) : AndroidViewModel(application) {
    private val bridge = AlpineRBridge(application)
    
    private val _output = MutableStateFlow<List<String>>(listOf("Welcome to droidR Studio Terminal"))
    val output: StateFlow<List<String>> = _output.asStateFlow()

    private val _plots = MutableStateFlow<List<File>>(emptyList())
    val plots: StateFlow<List<File>> = _plots.asStateFlow()

    init {
        // Pipe bridge output to our local output state
        viewModelScope.launch {
            bridge.output.collectLatest { bridgeLines ->
                if (bridgeLines.isNotEmpty()) {
                    // When bridge execution starts, it clears its output.
                    // We can choose to append or replace. 
                    // Given AlpineRBridge.execute() clears its output, 
                    // we should probably just append the header once and then follow bridge.
                    // But to keep it simple and match AlpineRBridge's behavior:
                    _output.value = bridgeLines
                }
            }
        }
    }

    fun runScript(code: String) {
        viewModelScope.launch {
            bridge.execute(code)
            scanForPlots()
        }
    }

    fun scanForPlots() {
        val guestRoot = bridge.getGuestRoot()
        if (guestRoot.exists() && guestRoot.isDirectory) {
            val plotFiles = guestRoot.listFiles { file ->
                val ext = file.extension.lowercase()
                file.isFile && (ext == "png" || ext == "pdf" || ext == "html" || ext == "json")
            }?.toList() ?: emptyList()
            _plots.value = plotFiles
        }
    }

    fun deletePlot(file: File) {
        if (file.exists()) {
            file.delete()
            scanForPlots()
        }
    }

    /** Copies a picked CSV into the R guest home so the Data Coach can load it as my_data. */
    fun importCsv(uri: Uri, onImported: (String) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val destination = File(bridge.getGuestRoot(), "imported_data.csv")
                getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                    destination.outputStream().use { output -> input.copyTo(output) }
                } ?: error("Unable to read the selected file.")
                withContext(Dispatchers.Main) { onImported("my_data") }
            } catch (exception: Exception) {
                withContext(Dispatchers.Main) { onError(exception.message ?: "Could not import the CSV file.") }
            }
        }
    }
    
    fun clearOutput() {
        // Note: bridge doesn't have a clear, but execute() clears it.
        _output.value = emptyList()
    }
}

package com.jay.droidrstudio

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class TerminalViewModel(application: Application) : AndroidViewModel(application) {
    val engine = PRootEngine(application)
    
    val plots: StateFlow<List<File>> = engine.plots

    init {
        fixRootfsPermissions()
    }

    private fun fixRootfsPermissions() {
        viewModelScope.launch(Dispatchers.IO) {
            val rootfsRoot = engine.getGuestRoot().parentFile
            if (rootfsRoot != null && rootfsRoot.exists()) {
                // Ensure critical Alpine directories exist and have proper permissions
                listOf(
                    "tmp", 
                    "var/cache/apk", 
                    "lib/apk/db", 
                    "etc/apk",
                    "root"
                ).forEach { path ->
                    val dir = File(rootfsRoot, path)
                    if (!dir.exists()) dir.mkdirs()
                    try {
                        dir.setWritable(true, false)
                        dir.setReadable(true, false)
                        dir.setExecutable(true, false)
                    } catch (e: Exception) {
                        Log.w("TerminalViewModel", "Failed to set permissions for $path: ${e.message}")
                    }
                }
            }
        }
    }

    fun scanForPlots() {
        engine.scanForPlots()
    }

    fun deletePlot(file: File) {
        if (file.exists()) {
            file.delete()
            engine.scanForPlots()
        }
    }

    /** Copies a picked CSV into the R guest home so the Data Coach can load it as my_data. */
    fun importCsv(uri: Uri, onImported: (String) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val destination = File(engine.getGuestRoot(), "imported_data.csv")
                getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                    destination.outputStream().use { output -> input.copyTo(output) }
                } ?: error("Unable to read the selected file.")
                withContext(Dispatchers.Main) { 
                    engine.scanForPlots()
                    onImported("my_data") 
                }
            } catch (exception: Exception) {
                withContext(Dispatchers.Main) { onError(exception.message ?: "Could not import the CSV file.") }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        engine.stopAll()
    }
}

package com.jay.droidrstudio

import android.content.Context
import android.net.Uri
import java.io.File

class FileSaver {
    /**
     * Saves the given content to a file named "runner.R" in the specified directory.
     */
    fun saveRunnerR(directory: File, content: String) {
        val file = File(directory, "runner.R")
        file.writeText(content)
    }

    /**
     * Exports a file to the provided URI (e.g., from Storage Access Framework).
     */
    fun exportFile(context: Context, sourceFile: File, destinationUri: Uri) {
        context.contentResolver.openOutputStream(destinationUri)?.use { output ->
            sourceFile.inputStream().use { input ->
                input.copyTo(output)
            }
        }
    }
}

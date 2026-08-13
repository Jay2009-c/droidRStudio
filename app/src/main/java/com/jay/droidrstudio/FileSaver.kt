package com.jay.droidrstudio

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract
import java.io.File

class DynamicCreateDocument : ActivityResultContract<Pair<String, String>, Uri?>() {
    override fun createIntent(context: Context, input: Pair<String, String>): Intent {
        return Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(input.second)
            .putExtra(Intent.EXTRA_TITLE, input.first)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
        return if (intent == null || resultCode != Activity.RESULT_OK) null else intent.data
    }
}

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

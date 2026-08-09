package com.jay.droidrstudio.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import java.io.File

class DownloadCompletedReceiver(
    private val targetDownloadId: Long? = null,
    private val onComplete: (Uri) -> Unit
) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            if (id != -1L && (targetDownloadId == null || id == targetDownloadId)) {
                val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                val query = DownloadManager.Query().setFilterById(id)
                downloadManager.query(query)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                        if (status == DownloadManager.STATUS_SUCCESSFUL) {
                            val fileUriString = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
                            fileUriString?.let { uriStr ->
                                val file = File(uriStr.toUri().path ?: return@let)
                                
                                Log.d("UpdateManager", "Download complete. File path: ${file.absolutePath}")
                                Log.d("UpdateManager", "File size: ${file.length()} bytes")

                                if (file.length() < 2048) { // Less than 2KB is definitely not an APK
                                    Log.e("UpdateManager", "Error: Downloaded file is too small. The URL likely pointed to a webpage instead of an APK.")
                                }

                                val contentUri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    file
                                )
                                onComplete(contentUri)
                            }
                        } else {
                            val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                            Log.e("UpdateManager", "Download failed. Status: $status, Reason: $reason")
                        }
                    }
                }
            }
        }
    }
}

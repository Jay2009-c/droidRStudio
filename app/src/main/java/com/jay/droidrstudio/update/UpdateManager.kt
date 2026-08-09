package com.jay.droidrstudio.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.core.net.toUri
import com.google.firebase.database.FirebaseDatabase
import com.jay.droidrstudio.BuildConfig
import kotlinx.coroutines.tasks.await

class UpdateManager(private val context: Context) {
    
    private val database = FirebaseDatabase.getInstance()
    private val updateRef = database.getReference("app_update")

    suspend fun checkForUpdates(): AppUpdate? {
        Log.d("UpdateManager", "Checking for updates...")
        val snapshot = updateRef.get().await()
        Log.d("UpdateManager", "Snapshot received. exists: ${snapshot.exists()}, value: ${snapshot.value}")
        
        val appUpdate = if (snapshot.exists()) {
            val latestVersionCode = (snapshot.child("latestVersionCode").value as? Long)?.toInt() ?: 0
            val latestVersionName = snapshot.child("latestVersionName").value?.toString() ?: ""
            val apkUrl = snapshot.child("apkUrl").value?.toString() ?: ""
            AppUpdate(latestVersionCode, latestVersionName, apkUrl)
        } else {
            null
        }
        
        Log.d("UpdateManager", "Fetched update info: $appUpdate")
        Log.d("UpdateManager", "Local version code: ${BuildConfig.VERSION_CODE}")
        
        return if (appUpdate != null && appUpdate.latestVersionCode > BuildConfig.VERSION_CODE) {
            appUpdate
        } else {
            null
        }
    }

    fun downloadUpdate(url: String, fileName: String): Long {
        Log.d("UpdateManager", "Starting download from URL: $url")
        val request = DownloadManager.Request(url.toUri())
            .setTitle("Updating droidR Studio")
            .setDescription("Downloading $fileName")
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return downloadManager.enqueue(request)
    }

    fun installUpdate(uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }
}

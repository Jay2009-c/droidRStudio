package com.jay.droidrstudio

import android.app.DownloadManager
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jay.droidrstudio.ui.theme.DroidRStudioTheme
import com.jay.droidrstudio.update.UpdateManager
import com.jay.droidrstudio.update.DownloadCompletedReceiver
import com.jay.droidrstudio.update.AppUpdate
import com.jay.droidrstudio.ui.UpdateDialog
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: TerminalViewModel = viewModel()
            DroidRStudioTheme {
                val context = LocalContext.current
                
                // OTA Update System
                var showUpdateDialog by remember { mutableStateOf<AppUpdate?>(null) }
                val updateManager = remember { UpdateManager(context) }
                var currentDownloadId by remember { mutableLongStateOf(-1L) }

                LaunchedEffect(Unit) {
                    try {
                        val update = updateManager.checkForUpdates()
                        if (update != null) showUpdateDialog = update
                    } catch (e: Exception) { e.printStackTrace() }
                }

                DisposableEffect(context, currentDownloadId) {
                    val receiver = DownloadCompletedReceiver(
                        targetDownloadId = if (currentDownloadId != -1L) currentDownloadId else null
                    ) { uri -> updateManager.installUpdate(uri) }
                    val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
                    ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
                    onDispose { context.unregisterReceiver(receiver) }
                }

                if (showUpdateDialog != null) {
                    UpdateDialog(
                        versionName = showUpdateDialog!!.latestVersionName,
                        onUpdate = {
                            currentDownloadId = updateManager.downloadUpdate(showUpdateDialog!!.apkUrl, "update.apk")
                            showUpdateDialog = null
                        },
                        onDismiss = { showUpdateDialog = null }
                    )
                }

                val assetExtractor = remember { AssetExtractor(context) }
                val extractionState by assetExtractor.state.collectAsState()
                
                val alpineDir = remember { File(context.filesDir, "alpine") }
                val versionFile = remember { File(alpineDir, ".version") }
                val currentVersion = "8" // Force re-extraction for new architecture
                
                val hasValidAlpineRoot = remember(alpineDir) {
                    File(alpineDir, "rootfs/usr/bin/Rscript").isFile && 
                    versionFile.exists() && versionFile.readText().trim() == currentVersion
                }

                var isSetupComplete by remember { mutableStateOf(hasValidAlpineRoot) }

                if (!isSetupComplete) {
                    SetupScreen(state = extractionState)
                    LaunchedEffect(Unit) {
                        try {
                            if (!hasValidAlpineRoot) {
                                assetExtractor.extractAsset(alpineDir)
                                versionFile.writeText(currentVersion)
                            }
                            isSetupComplete = true
                        } catch (e: Exception) { /* Handled by UI */ }
                    }
                } else {
                    val initialText = remember {
                        val file = File(context.cacheDir, "runner.R")
                        if (file.exists()) file.readText() else ""
                    }
                    val textFieldState = rememberTextFieldState(initialText = initialText)
                    
                    MainWorkspace(
                        viewModel = viewModel,
                        editorState = textFieldState
                    )
                }
            }
        }
    }
}

@Composable
fun SetupScreen(state: ExtractionState) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                Text(
                    text = "Setting Up Environment",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(24.dp))
                when (state) {
                    is ExtractionState.Extracting -> {
                        Text(
                            text = "Unpacking Alpine Filesystem... & Setting PRoot Environment",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "${(state.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    is ExtractionState.Error -> {
                        Text(
                            text = "Error: ${state.message}",
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                    }
                    else -> {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}

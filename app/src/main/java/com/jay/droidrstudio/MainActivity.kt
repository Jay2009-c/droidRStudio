package com.jay.droidrstudio

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.core.content.ContextCompat
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.BrowserUpdated
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.jay.droidrstudio.ui.theme.DroidRStudioTheme
import com.jay.droidrstudio.update.UpdateManager
import com.jay.droidrstudio.update.DownloadCompletedReceiver
import com.jay.droidrstudio.update.AppUpdate
import com.jay.droidrstudio.ui.UpdateDialog
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
sealed interface AppNavKey : NavKey {
    @Serializable
    data object Editor : AppNavKey
    @Serializable
    data object Terminal : AppNavKey
    @Serializable
    data class Viewer(val filePath: String) : AppNavKey
    @Serializable
    data class DataViewer(val filePath: String) : AppNavKey
}

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

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val terminalViewModel: TerminalViewModel = viewModel()
            DroidRStudioTheme {
                val context = LocalContext.current
                
                // OTA Update System
                var showUpdateDialog by remember { mutableStateOf<AppUpdate?>(null) }
                val updateManager = remember { UpdateManager(context) }
                var currentDownloadId by remember { mutableLongStateOf(-1L) }
                val snackbarHostState = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()

                // Manual Update Function
                val checkUpdateManual = {
                    scope.launch {
                        try {
                            val update = updateManager.checkForUpdates()
                            if (update != null) {
                                showUpdateDialog = update
                            } else {
                                snackbarHostState.showSnackbar("App is up to date")
                            }
                        } catch (e: Exception) {
                            Log.e("MainActivity", "Update check failed", e)
                            snackbarHostState.showSnackbar("Error: ${e.localizedMessage ?: "Unknown error"}")
                        }
                    }
                }

                LaunchedEffect(Unit) {
                    try {
                        val update = updateManager.checkForUpdates()
                        if (update != null) {
                            showUpdateDialog = update
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                DisposableEffect(context, currentDownloadId) {
                    val receiver = DownloadCompletedReceiver(
                        targetDownloadId = if (currentDownloadId != -1L) currentDownloadId else null
                    ) { uri ->
                        updateManager.installUpdate(uri)
                    }
                    val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
                    ContextCompat.registerReceiver(
                        context,
                        receiver,
                        filter,
                        ContextCompat.RECEIVER_NOT_EXPORTED
                    )
                    onDispose {
                        context.unregisterReceiver(receiver)
                    }
                }

                if (showUpdateDialog != null) {
                    UpdateDialog(
                        versionName = showUpdateDialog!!.latestVersionName,
                        onUpdate = {
                            currentDownloadId = updateManager.downloadUpdate(
                                showUpdateDialog!!.apkUrl,
                                "update.apk"
                            )
                            showUpdateDialog = null
                        },
                        onDismiss = { showUpdateDialog = null }
                    )
                }

                val assetExtractor = remember { AssetExtractor(context) }
                val extractionState by assetExtractor.state.collectAsState()
                
                val alpineDir = remember { File(context.filesDir, "alpine") }
                val versionFile = remember { File(alpineDir, ".version") }
                val currentVersion = "7" // Increment this to force re-extraction
                
                val hasValidAlpineRoot = remember(alpineDir) {
                    File(alpineDir, "rootfs/usr/bin/Rscript").isFile && 
                    versionFile.exists() && versionFile.readText().trim() == currentVersion
                }

                var isSetupComplete by remember {
                    mutableStateOf(hasValidAlpineRoot)
                }

                if (!isSetupComplete) {
                    SetupScreen(state = extractionState)
                    LaunchedEffect(Unit) {
                        try {
                            if (!hasValidAlpineRoot) {
                                assetExtractor.extractAsset(alpineDir)
                                versionFile.writeText(currentVersion)
                            }
                            isSetupComplete = true
                        } catch (e: Exception) {
                            // Error state handled by SetupScreen via assetExtractor.state
                        }
                    }
                } else {
                    MainContent(
                        terminalViewModel = terminalViewModel,
                        snackbarHostState = snackbarHostState,
                        onCheckForUpdates = { checkUpdateManual() }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MainContent(
    terminalViewModel: TerminalViewModel,
    snackbarHostState: SnackbarHostState,
    onCheckForUpdates: () -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val scope = rememberCoroutineScope()
    
    val sheetState = rememberModalBottomSheetState()
    var showQuickPlot by remember { mutableStateOf(false) }
    var showDataTools by remember { mutableStateOf(false) }
    var csvImportCallback by remember { mutableStateOf<((String) -> Unit)?>(null) }

    var pendingFileToExport by remember { mutableStateOf<File?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        DynamicCreateDocument()
    ) { uri ->
        uri?.let {
            pendingFileToExport?.let { file ->
                FileSaver().exportFile(context, file, it)
                pendingFileToExport = null
            }
        }
    }
    val csvImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            terminalViewModel.importCsv(
                uri = it,
                onImported = { datasetName ->
                    csvImportCallback?.invoke(datasetName)
                    csvImportCallback = null
                },
                onError = { csvImportCallback = null }
            )
        }
    }

    val initialText = remember {
        val file = File(context.cacheDir, "runner.R")
        if (file.exists()) file.readText() else ""
    }
    val textFieldState = rememberTextFieldState(initialText = initialText)

    val backstack = rememberNavBackStack(AppNavKey.Editor as NavKey)
    val showTerminal = backstack.any { it == AppNavKey.Terminal }

    var terminalHeight by remember { mutableStateOf(240.dp) }
    var isTerminalFullScreen by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("droidR Studio", style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                actions = {
                    IconButton(onClick = onCheckForUpdates) {
                        Icon(
                            Icons.Filled.BrowserUpdated,
                            contentDescription = "Check for Updates",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = { showDataTools = true }) {
                        Icon(
                            Icons.Filled.Build,
                            contentDescription = "Data Tools",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = { showQuickPlot = true }) {
                        Icon(
                            Icons.Filled.AutoGraph,
                            contentDescription = "Quick Plot",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (showTerminal) {
                        IconButton(onClick = {
                            val code = textFieldState.text.toString()
                            FileSaver().saveRunnerR(context.cacheDir, code)
                            terminalViewModel.runScript(code)
                        }) {
                            Icon(
                                Icons.Filled.PlayArrow,
                                contentDescription = "Run Script",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (!showTerminal) {
                LargeFloatingActionButton(
                    onClick = {
                        val code = textFieldState.text.toString()
                        // Explicit save before running
                        FileSaver().saveRunnerR(context.cacheDir, code)

                        terminalViewModel.runScript(code)
                        if (backstack.lastOrNull() != AppNavKey.Terminal) {
                            backstack.add(AppNavKey.Terminal)
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Run R Script")
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            if (!isTerminalFullScreen) {
                Box(modifier = Modifier.weight(1f)) {
                    EditorScreen(state = textFieldState)
                }
            }

            if (showTerminal) {
                if (!isTerminalFullScreen) {
                    VerticalSplitter(
                        onResize = { delta ->
                            terminalHeight = (terminalHeight - delta).coerceIn(100.dp, screenHeight - 160.dp)
                        }
                    )
                }

                Box(
                    modifier = if (isTerminalFullScreen) Modifier.fillMaxSize()
                    else Modifier.height(terminalHeight)
                ) {
                    NavDisplay(
                        backStack = backstack,
                        onBack = {
                            isTerminalFullScreen = false
                            backstack.removeLastOrNull()
                        }
                    ) { key ->
                        when (key) {
                            is AppNavKey.Terminal -> NavEntry(
                                key = key
                            ) {
                                TerminalScreen(
                                    viewModel = terminalViewModel,
                                    isFullScreen = isTerminalFullScreen,
                                    onFullScreenToggle = { isTerminalFullScreen = !isTerminalFullScreen },
                                    onClose = {
                                        isTerminalFullScreen = false
                                        backstack.removeLastOrNull()
                                    },
                                    onExportPlot = { file ->
                                        pendingFileToExport = file
                                        val mimeType = if (file.extension.lowercase() == "pdf") "application/pdf" else "image/png"
                                        exportLauncher.launch(file.name to mimeType)
                                    },
                                    onViewPlot = { file ->
                                        if (file.extension.lowercase() == "json") {
                                            backstack.add(AppNavKey.DataViewer(file.absolutePath))
                                        } else {
                                            backstack.add(AppNavKey.Viewer(file.absolutePath))
                                        }
                                    }
                                )
                            }

                            is AppNavKey.Viewer -> NavEntry(key) {
                                ViewerScreen(
                                    file = File(key.filePath),
                                    onBack = { backstack.removeLastOrNull() },
                                    onExport = {
                                        val file = File(key.filePath)
                                        pendingFileToExport = file
                                        val extension = file.extension.lowercase()
                                        val mimeType = when (extension) {
                                            "pdf" -> "application/pdf"
                                            "html" -> "text/html"
                                            else -> "image/png"
                                        }
                                        exportLauncher.launch(file.name to mimeType)
                                    }
                                )
                            }

                            is AppNavKey.DataViewer -> NavEntry(key) {
                                DataViewerScreen(
                                    file = File(key.filePath),
                                    onBack = { backstack.removeLastOrNull() },
                                    onExport = {
                                        val file = File(key.filePath)
                                        pendingFileToExport = file
                                        exportLauncher.launch(file.name to "application/json")
                                    }
                                )
                            }

                            else -> NavEntry(key) { Box(Modifier.fillMaxSize()) }
                        }
                    }
                }
            }
        }

        if (showQuickPlot) {
            ModalBottomSheet(
                onDismissRequest = { showQuickPlot = false },
                sheetState = sheetState
            ) {
                QuickPlotWizard(
                    onGenerate = { rCode ->
                        showQuickPlot = false
                        // Update text state so user can see/edit the generated code
                        textFieldState.setTextAndPlaceCursorAtEnd(rCode)
                        
                        // Execute immediately
                        FileSaver().saveRunnerR(context.cacheDir, rCode)
                        terminalViewModel.runScript(rCode)
                        
                        if (backstack.lastOrNull() != AppNavKey.Terminal) {
                            backstack.add(AppNavKey.Terminal)
                        }
                    }
                )
            }
        }

        if (showDataTools) {
            ModalBottomSheet(
                onDismissRequest = { showDataTools = false },
                sheetState = sheetState
            ) {
                DataToolsWizard(
                    onGenerate = { rCode ->
                        showDataTools = false
                        textFieldState.setTextAndPlaceCursorAtEnd(rCode)
                        
                        FileSaver().saveRunnerR(context.cacheDir, rCode)
                        terminalViewModel.runScript(rCode)
                        if (backstack.lastOrNull() != AppNavKey.Terminal) {
                            backstack.add(AppNavKey.Terminal)
                        }
                    },
                    onImportCsv = { onImported ->
                        csvImportCallback = onImported
                        csvImportLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "application/vnd.ms-excel"))
                    }
                )
            }
        }
    }
}

@Composable
fun VerticalSplitter(onResize: (Dp) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(12.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onResize(dragAmount.y.toDp())
                }
            }
    ) {
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(4.dp)
                .background(MaterialTheme.colorScheme.onSurfaceVariant, RoundedCornerShape(2.dp))
                .align(Alignment.Center)
        )
    }
}

@Composable
fun SetupScreen(state: ExtractionState) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                "Setting up environment...",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(16.dp))

            when (state) {
                is ExtractionState.Extracting -> {
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "${(state.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                is ExtractionState.Error -> {
                    Text(
                        "Error: ${state.message}",
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

@Preview(name = "Phone", device = "spec:width=360dp,height=800dp,dpi=441", showBackground = true)
@Preview(name = "Tablet", device = "spec:width=1280dp,height=800dp,dpi=240", showBackground = true)
@Composable
fun MainPreview() {
    DroidRStudioTheme {
        EditorScreen(state = rememberTextFieldState())
    }
}

package com.jay.droidrstudio

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BrowserUpdated
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Output
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jay.droidrstudio.ui.UpdateDialog
import com.jay.droidrstudio.update.AppUpdate
import com.jay.droidrstudio.update.UpdateManager
import kotlinx.coroutines.launch
import java.io.File

enum class WorkspaceTab(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    EDITOR("Editor", Icons.Default.Code),
    R_OUTPUT("R Output", Icons.Default.Output),
    TERMINAL("Terminal", Icons.Default.Terminal),
    PLOTS("Plots", Icons.Default.BarChart)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainWorkspace(
    viewModel: TerminalViewModel,
    editorState: TextFieldState
) {
    val engine = viewModel.engine
    var selectedTab by remember { mutableStateOf(WorkspaceTab.EDITOR) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Wizards & Update State
    var showQuickPlot by remember { mutableStateOf(false) }
    var showDataTools by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf<AppUpdate?>(null) }
    val updateManager = remember { UpdateManager(context) }
    var csvImportCallback by remember { mutableStateOf<((String) -> Unit)?>(null) }
    
    // Plot View State
    var selectedPlotFile by remember { mutableStateOf<File?>(null) }
    var pendingFileToExport by remember { mutableStateOf<File?>(null) }

    val csvImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            viewModel.importCsv(
                uri = it,
                onImported = { datasetName ->
                    csvImportCallback?.invoke(datasetName)
                    csvImportCallback = null
                },
                onError = { csvImportCallback = null }
            )
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = DynamicCreateDocument()
    ) { uri: Uri? ->
        uri?.let {
            pendingFileToExport?.let { file ->
                FileSaver().exportFile(context, file, it)
                pendingFileToExport = null
            }
        }
    }

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
                Log.e("MainWorkspace", "Update check failed", e)
                snackbarHostState.showSnackbar("Error: ${e.localizedMessage ?: "Unknown error"}")
            }
        }
        Unit
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("droidR-Studio", style = MaterialTheme.typography.titleLarge) },
                actions = {
                    IconButton(onClick = { checkUpdateManual() }) {
                        Icon(Icons.Filled.BrowserUpdated, contentDescription = "Updates", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { showDataTools = true }) {
                        Icon(Icons.Filled.Build, contentDescription = "Data Tools", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { showQuickPlot = true }) {
                        Icon(Icons.Filled.AutoGraph, contentDescription = "Quick Plot", tint = MaterialTheme.colorScheme.primary)
                    }
                    if (selectedTab == WorkspaceTab.EDITOR) {
                        IconButton(onClick = {
                            engine.runRScriptDirectly(editorState.text.toString())
                            selectedTab = WorkspaceTab.R_OUTPUT
                        }) {
                            Icon(Icons.Filled.Bolt, contentDescription = "Run", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                WorkspaceTab.entries.forEach { tab ->
                    NavigationBarItem(
                        icon = { Icon(tab.icon, contentDescription = tab.title) },
                        label = { Text(tab.title) },
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding).fillMaxSize()) {
            when (selectedTab) {
                WorkspaceTab.EDITOR -> EditorTab(editorState) {
                    engine.runRScriptDirectly(editorState.text.toString())
                    selectedTab = WorkspaceTab.R_OUTPUT
                }
                WorkspaceTab.R_OUTPUT -> OutputTab(engine)
                WorkspaceTab.TERMINAL -> TerminalTab(engine)
                WorkspaceTab.PLOTS -> {
                    LaunchedEffect(Unit) {
                        viewModel.scanForPlots()
                    }
                    PlotScreen(
                        viewModel = viewModel,
                        onExport = { file ->
                            pendingFileToExport = file
                            val mimeType = if (file.extension.lowercase() == "pdf") "application/pdf" else "image/png"
                            exportLauncher.launch(file.name to mimeType)
                        },
                        onView = { selectedPlotFile = it }
                    )
                }
            }
        }

        // Modals
        if (showQuickPlot) {
            ModalBottomSheet(onDismissRequest = { showQuickPlot = false }) {
                QuickPlotWizard(onGenerate = { rCode ->
                    showQuickPlot = false
                    editorState.setTextAndPlaceCursorAtEnd(rCode)
                    engine.runRScriptDirectly(rCode)
                    selectedTab = WorkspaceTab.R_OUTPUT
                })
            }
        }

        if (showDataTools) {
            ModalBottomSheet(onDismissRequest = { showDataTools = false }) {
                DataToolsWizard(
                    onGenerate = { rCode ->
                        showDataTools = false
                        editorState.setTextAndPlaceCursorAtEnd(rCode)
                        engine.runRScriptDirectly(rCode)
                        selectedTab = WorkspaceTab.R_OUTPUT
                    },
                    onImportCsv = { onImported ->
                        csvImportCallback = onImported
                        csvImportLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "application/vnd.ms-excel"))
                    }
                )
            }
        }

        if (showUpdateDialog != null) {
            UpdateDialog(
                versionName = showUpdateDialog!!.latestVersionName,
                onUpdate = {
                    updateManager.downloadUpdate(showUpdateDialog!!.apkUrl, "update.apk")
                    showUpdateDialog = null
                },
                onDismiss = { showUpdateDialog = null }
            )
        }

        // Plot View Overlay
        if (selectedPlotFile != null) {
            val file = selectedPlotFile!!
            if (file.extension.lowercase() == "json") {
                DataViewerDialog(file = file, onDismiss = { selectedPlotFile = null })
            } else {
                ViewerDialog(
                    file = file, 
                    onDismiss = { selectedPlotFile = null },
                    onExport = {
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
        }
    }
}

@Composable
fun DataViewerDialog(file: File, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        text = {
            Box(Modifier.fillMaxSize()) {
                DataViewerScreen(file = file, onBack = onDismiss, onExport = {})
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    )
}

@Composable
fun ViewerDialog(file: File, onDismiss: () -> Unit, onExport: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { 
            Row {
                TextButton(onClick = onExport) { Text("Export") }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
        text = {
            Box(Modifier.fillMaxSize()) {
                ViewerScreen(file = file, onBack = onDismiss, onExport = onExport)
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    )
}

@Composable
fun EditorTab(state: TextFieldState, onRun: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        Box(modifier = Modifier.weight(1f)) {
            EditorScreen(state = state)
        }
        Button(
            onClick = onRun,
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Icon(Icons.Default.Bolt, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("⚡ Run Code in Rscript")
        }
    }
}

@Composable
fun OutputTab(engine: PRootEngine) {
    val output by engine.rOutput.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(output.size) {
        if (output.isNotEmpty()) {
            listState.animateScrollToItem(output.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
    ) {
        // Output Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1C1C1C))
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "R Console Output",
                style = MaterialTheme.typography.labelSmall,
                color = Color.LightGray,
                fontFamily = FontFamily.Monospace
            )
            IconButton(
                onClick = { engine.clearROutput() },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    Icons.Default.DeleteSweep,
                    contentDescription = "Clear",
                    tint = Color.Gray,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)
        ) {
            items(output) { line ->
                Text(
                    text = line,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = if (line.contains("Error", ignoreCase = true)) Color.Red else Color.Green,
                        lineHeight = 18.sp
                    )
                )
            }
        }
    }
}

@Composable
fun TerminalTab(engine: PRootEngine) {
    val output by engine.terminalOutput.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }
    
    // Command history
    val commandHistory = remember { mutableStateListOf<String>() }
    var historyIndex by remember { mutableIntStateOf(-1) }

    LaunchedEffect(Unit) {
        engine.startPersistentTerminal()
    }

    LaunchedEffect(output.size) {
        if (output.isNotEmpty()) {
            listState.animateScrollToItem(output.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
            .imePadding()
    ) {
        // Terminal Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1C1C1C))
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Terminal (Alpine Linux)",
                style = MaterialTheme.typography.labelSmall,
                color = Color.LightGray,
                fontFamily = FontFamily.Monospace
            )
            IconButton(
                onClick = { engine.clearTerminalOutput() },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    Icons.Default.DeleteSweep,
                    contentDescription = "Clear",
                    tint = Color.Gray,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Box(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                items(output) { line ->
                    if (line.contains("[H[2J") || line.contains("[3J")) {
                        // This is a simplified way to handle clear screen within a line-based flow
                        // In a real terminal emulator this would wipe the screen
                        // For now we just skip the garbage characters
                        val cleaned = line.replace(Regex("\u001B\\[[H23J]*"), "")
                        if (cleaned.isNotBlank()) {
                            TerminalLine(cleaned)
                        }
                    } else {
                        TerminalLine(line)
                    }
                }
            }
        }
        
        // Utility Keyboard Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .background(Color(0xFF121212))
                .padding(vertical = 4.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            TerminalKey("Tab") { input += "\t" }
            TerminalKey("Ctrl") { /* Placeholder */ }
            TerminalKey("Esc") { input = "" }
            TerminalKey("↑") { 
                if (commandHistory.isNotEmpty()) {
                    if (historyIndex == -1) historyIndex = commandHistory.size - 1
                    else if (historyIndex > 0) historyIndex--
                    input = commandHistory[historyIndex]
                }
            }
            TerminalKey("↓") { 
                if (commandHistory.isNotEmpty() && historyIndex != -1) {
                    if (historyIndex < commandHistory.size - 1) {
                        historyIndex++
                        input = commandHistory[historyIndex]
                    } else {
                        historyIndex = -1
                        input = ""
                    }
                }
            }
            TerminalKey("/") { input += "/" }
            TerminalKey("-") { input += "-" }
            TerminalKey("|") { input += "|" }
            TerminalKey("Pkg") { input = "apk add " }
        }

        Surface(
            color = Color(0xFF000000),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 2.dp)
            ) {
                Text(
                    "root# ",
                    color = Color(0xFF00FF00),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 12.dp)
                )
                TextField(
                    value = input,
                    onValueChange = { 
                        input = it 
                        if (it.isEmpty()) historyIndex = -1
                    },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Command...", fontSize = 13.sp, color = Color.DarkGray) },
                    singleLine = true,
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        color = Color.White
                    ),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = Color.White
                    ),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = androidx.compose.ui.text.input.ImeAction.Send
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onSend = {
                            if (input.isNotBlank()) {
                                val cmd = input.trim()
                                if (cmd == "clear") {
                                    engine.clearTerminalOutput()
                                } else {
                                    engine.sendTerminalCommand(cmd)
                                    commandHistory.add(cmd)
                                }
                                input = ""
                                historyIndex = -1
                            }
                        }
                    )
                )
                IconButton(
                    onClick = {
                        if (input.isNotBlank()) {
                            val cmd = input.trim()
                            if (cmd == "clear") {
                                engine.clearTerminalOutput()
                            } else {
                                engine.sendTerminalCommand(cmd)
                                commandHistory.add(cmd)
                            }
                            input = ""
                            historyIndex = -1
                        }
                    },
                    modifier = Modifier.padding(end = 4.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.LightGray)
                }
            }
        }
    }
}

@Composable
fun TerminalLine(line: String) {
    Text(
        text = parseAnsi(line),
        style = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            lineHeight = 15.sp,
            color = Color.White
        ),
        modifier = Modifier.padding(horizontal = 8.dp)
    )
}

@Composable
fun TerminalKey(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        modifier = Modifier.height(32.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF333333),
            contentColor = Color.White
        ),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
    ) {
        Text(label, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
    }
}

/**
 * Basic ANSI color parser for terminal output.
 */
fun parseAnsi(text: String): AnnotatedString {
    return buildAnnotatedString {
        var currentIndex = 0
        val ansiRegex = Regex("\u001B\\[([0-9;]*)m")
        
        val matches = ansiRegex.findAll(text)
        
        for (match in matches) {
            // Append plain text before the match
            append(text.substring(currentIndex, match.range.first))
            
            val code = match.groupValues[1]
            when (code) {
                "31" -> pushStyle(SpanStyle(color = Color.Red))
                "32" -> pushStyle(SpanStyle(color = Color.Green))
                "33" -> pushStyle(SpanStyle(color = Color.Yellow))
                "34" -> pushStyle(SpanStyle(color = Color.Blue))
                "35" -> pushStyle(SpanStyle(color = Color.Magenta))
                "36" -> pushStyle(SpanStyle(color = Color.Cyan))
                "0" -> pop() // Reset
                else -> { /* Ignore other codes for now */ }
            }
            currentIndex = match.range.last + 1
        }
        
        // Append remaining text
        if (currentIndex < text.length) {
            append(text.substring(currentIndex))
        }
    }
}

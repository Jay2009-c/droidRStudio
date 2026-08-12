package com.jay.droidrstudio

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

@Composable
fun TerminalScreen(
    viewModel: TerminalViewModel,
    isFullScreen: Boolean,
    onFullScreenToggle: () -> Unit,
    onClose: () -> Unit,
    onExportPlot: (File) -> Unit = {},
    onViewPlot: (File) -> Unit = {}
) {
    val output by viewModel.output.collectAsState()
    val terminalOutput by viewModel.terminalOutput.collectAsState()
    
    val listState = rememberLazyListState()
    val terminalListState = rememberLazyListState()
    
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Output", "Terminal", "Plots")

    var terminalInput by remember { mutableStateOf("") }

    // Auto-scroll to bottom when new output arrives
    LaunchedEffect(output.size) {
        if (output.isNotEmpty() && selectedTab == 0) {
            listState.animateScrollToItem(output.size - 1)
        }
    }

    LaunchedEffect(terminalOutput.size) {
        if (terminalOutput.isNotEmpty() && selectedTab == 1) {
            terminalListState.animateScrollToItem(terminalOutput.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black) // Terminal look
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = Color.LightGray,
                divider = {},
                modifier = Modifier.weight(1f)
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, style = MaterialTheme.typography.labelLarge) }
                    )
                }
            }

            Row {
                if (selectedTab == 0 || selectedTab == 1) {
                    IconButton(onClick = { 
                        if (selectedTab == 0) viewModel.clearOutput() 
                        else viewModel.clearTerminalOutput()
                    }) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear Output",
                            tint = Color.LightGray
                        )
                    }
                }
                IconButton(onClick = onFullScreenToggle) {
                    Icon(
                        imageVector = if (isFullScreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        contentDescription = if (isFullScreen) "Exit Full Screen" else "Full Screen",
                        tint = Color.LightGray
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Terminal",
                        tint = Color.LightGray
                    )
                }
            }
        }

        Box(modifier = Modifier.weight(1f).padding(8.dp)) {
            when (selectedTab) {
                0 -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
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
                1 -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            state = terminalListState,
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            items(terminalOutput) { line ->
                                Text(
                                    text = line,
                                    style = TextStyle(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 13.sp,
                                        color = if (line.startsWith("#")) Color.Yellow 
                                                else if (line.contains("Error", ignoreCase = true)) Color.Red 
                                                else Color.Cyan,
                                        lineHeight = 18.sp
                                    )
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().background(Color(0xFF1A1A1A))
                        ) {
                            Text(
                                "# ",
                                color = Color.Yellow,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                            TextField(
                                value = terminalInput,
                                onValueChange = { terminalInput = it },
                                modifier = Modifier.fillMaxWidth(),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    cursorColor = Color.White,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                textStyle = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 14.sp
                                ),
                                singleLine = true,
                                placeholder = {
                                    Text(
                                        "apk add ..., ls, sh script.sh",
                                        color = Color.DarkGray,
                                        fontSize = 12.sp
                                    )
                                },
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    imeAction = androidx.compose.ui.text.input.ImeAction.Done
                                ),
                                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                                    onDone = {
                                        if (terminalInput.isNotBlank()) {
                                            viewModel.executeShellCommand(terminalInput)
                                            terminalInput = ""
                                        }
                                    }
                                )
                            )
                        }
                    }
                }
                2 -> {
                    PlotScreen(
                        viewModel = viewModel,
                        onExport = onExportPlot,
                        onView = onViewPlot
                    )
                }
            }
        }
    }
}

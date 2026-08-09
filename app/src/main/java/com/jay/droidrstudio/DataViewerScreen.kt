package com.jay.droidrstudio

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataViewerScreen(
    file: File,
    onBack: () -> Unit,
    onExport: () -> Unit
) {
    val moshi = remember { Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build() }
    val type = remember { Types.newParameterizedType(List::class.java, Map::class.java, String::class.java, Any::class.java) }
    val adapter = remember { moshi.adapter<List<Map<String, Any>>>(type) }
    
    val data = remember(file) {
        try {
            val json = file.readText()
            adapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    val columns = remember(data) {
        if (data.isNotEmpty()) data.first().keys.toList() else emptyList()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(file.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onExport) {
                        Icon(Icons.Default.FileDownload, contentDescription = "Export")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (data.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(innerPadding).background(Color.White), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text("No data to display or failed to load JSON.")
            }
        } else {
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .horizontalScroll(rememberScrollState())
                    .background(Color.White)
            ) {
                LazyColumn(modifier = Modifier.fillMaxHeight()) {
                    // Header
                    item {
                        Row(modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
                            columns.forEach { col ->
                                Text(
                                    text = col,
                                    modifier = Modifier
                                        .width(150.dp)
                                        .padding(8.dp),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    // Rows
                    items(data) { row ->
                        Row {
                            columns.forEach { col ->
                                Text(
                                    text = row[col]?.toString() ?: "NA",
                                    modifier = Modifier
                                        .width(150.dp)
                                        .padding(8.dp),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                        HorizontalDivider(thickness = 0.5.dp, color = Color.LightGray)
                    }
                }
            }
        }
    }
}

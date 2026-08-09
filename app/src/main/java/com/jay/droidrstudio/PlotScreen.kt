package com.jay.droidrstudio

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.io.File

@Composable
fun PlotScreen(
    viewModel: TerminalViewModel,
    onExport: (File) -> Unit,
    onView: (File) -> Unit
) {
    val plots by viewModel.plots.collectAsState()

    if (plots.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "No plots generated yet.\nUse png(\"file.png\") or pdf(\"file.pdf\") in R.",
                color = Color.Gray,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(8.dp),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(plots) { plotFile ->
                PlotItem(
                    file = plotFile,
                    onDelete = { viewModel.deletePlot(plotFile) },
                    onExport = { onExport(plotFile) },
                    onView = { onView(plotFile) }
                )
            }
        }
    }
}

@Composable
fun PlotItem(
    file: File,
    onDelete: () -> Unit,
    onExport: () -> Unit,
    onView: () -> Unit
) {
    Card(
        modifier = Modifier.clickable(onClick = onView),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column {
            val extension = file.extension.lowercase()
            if (extension != "png") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when (extension) {
                            "pdf" -> Icons.Default.PictureAsPdf
                            "json" -> Icons.Default.TableChart
                            "html" -> Icons.Default.Html
                            else -> Icons.Default.Description
                        },
                        contentDescription = "$extension File",
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            } else {
                AsyncImage(
                    model = file,
                    contentDescription = file.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentScale = ContentScale.Fit
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 1
                )
                IconButton(onClick = onView) {
                    Icon(Icons.Default.Visibility, contentDescription = "View Plot", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onExport) {
                    Icon(Icons.Default.FileDownload, contentDescription = "Export Plot", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete Plot", modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

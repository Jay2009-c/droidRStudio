package com.jay.droidrstudio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private data class DataCoachTask(val title: String, val description: String, val result: String)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DataToolsWizard(onGenerate: (String) -> Unit, onImportCsv: ((String) -> Unit) -> Unit) {
    val beginnerTasks = listOf(
        DataCoachTask("Understand my data", "Comprehensive summary using cli and psych.", "Result appears in the console."),
        DataCoachTask("Data wrangling", "Clean and filter data using dplyr + tidyr.", "A table preview opens after it runs."),
        DataCoachTask("Correlation analysis", "Professional correlation heatmap using corrplot.", "A colourful correlation chart opens."),
        DataCoachTask("Interactive visualization", "Create zoomable Plotly charts from data.", "An interactive chart opens in browser."),
        DataCoachTask("Date processing", "Parse and manipulate dates using lubridate.", "A date-sorted table preview opens.")
    )
    val advancedTasks = listOf(
        DataCoachTask("Smart CSV Import (readr)", "Fast CSV reading with intelligent type detection.", "Dataset is loaded into environment."),
        DataCoachTask("Color Analysis (farver)", "Convert colors between spaces and compute distances.", "Color details appear in console."),
        DataCoachTask("Fast data summary", "High-performance calculation using data.table.", "Summary appears in console."),
        DataCoachTask("API data import", "Download JSON from public HTTPS APIs.", "A table preview opens after it runs."),
        DataCoachTask("String analysis", "Text mining and pattern analysis with stringr.", "Statistics appear in console."),
        DataCoachTask("Distribution plots", "Interactive histograms using plotly.", "An interactive histogram opens."),
        DataCoachTask("Time series", "Visualize temporal data with plotly.", "A time series chart opens."),
        DataCoachTask("Numerical statistics", "Deep statistical analysis using psych.", "Detailed stats table appears.")
    )
    var selectedTask by remember { mutableStateOf(beginnerTasks.first()) }
    var dataSource by remember { mutableStateOf("Car example") }
    var customDataset by remember { mutableStateOf("") }
    var apiUrl by remember { mutableStateOf("https://") }
    var dateColumn by remember { mutableStateOf("date") }
    var showAdvanced by remember { mutableStateOf(false) }

    val dataset = when (dataSource) {
        "Car example" -> "mtcars"
        "Flower example" -> "iris"
        "Air-quality example" -> "airquality"
        else -> customDataset
    }
    val needsUrl = selectedTask.title == "API data import"
    val needsDate = selectedTask.title == "Date processing"
    val canRun = if (needsUrl) apiUrl.startsWith("https://") else QuickPlotCodeGenerator.isSafeDatasetName(dataset)

    Column(Modifier.fillMaxWidth().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("Data Science Coach", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(6.dp))
        Text("Professional packages: dplyr, tidyr, readr, stringr, lubridate, data.table, plotly, cli, farver, psych.", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(22.dp))

        CoachSectionTitle("1. Select a Tool")
        beginnerTasks.forEach { task ->
            TaskCard(task = task, selected = task == selectedTask, onClick = { selectedTask = task })
            Spacer(Modifier.height(8.dp))
        }

        TextButton(onClick = { showAdvanced = !showAdvanced }, modifier = Modifier.fillMaxWidth()) {
            Text(if (showAdvanced) "Show basic tools" else "More advanced tools")
        }
        if (showAdvanced) {
            advancedTasks.forEach { task ->
                TaskCard(task = task, selected = task == selectedTask, onClick = { selectedTask = task })
                Spacer(Modifier.height(8.dp))
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
        CoachSectionTitle("2. Configuration")
        
        if (needsUrl) {
            OutlinedTextField(
                value = apiUrl, onValueChange = { apiUrl = it }, modifier = Modifier.fillMaxWidth(),
                label = { Text("Public HTTPS API URL") }, singleLine = true,
                isError = apiUrl.isNotBlank() && !apiUrl.startsWith("https://")
            )
        } else if (selectedTask.title == "Smart CSV Import (readr)") {
            Button(onClick = { onImportCsv { importedName -> customDataset = importedName } }, modifier = Modifier.fillMaxWidth()) {
                Text("Select CSV to Import")
            }
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Car example", "Flower example", "Air-quality example", "My loaded data").forEach { source ->
                    FilterChip(selected = dataSource == source, onClick = { dataSource = source }, label = { Text(source) })
                }
            }
            if (dataSource == "My loaded data") {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = customDataset, onValueChange = { customDataset = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text("R Dataset Name") }, singleLine = true,
                    isError = customDataset.isNotBlank() && !QuickPlotCodeGenerator.isSafeDatasetName(customDataset)
                )
            }
            
            if (needsDate) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = dateColumn, onValueChange = { dateColumn = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Date Column Name") }, singleLine = true
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
            Column(Modifier.padding(14.dp)) {
                Text("Workflow Insight", fontWeight = FontWeight.SemiBold)
                Text(selectedTask.result, style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                val code = when (selectedTask.title) {
                    "Smart CSV Import (readr)" -> QuickPlotCodeGenerator.generateGuidedTask("Smart Import (readr)", "my_data", "")
                    "Color Analysis (farver)" -> QuickPlotCodeGenerator.generateGuidedTask("Color Analysis", "mtcars", "")
                    "Time series" -> QuickPlotCodeGenerator.generateGuidedTask("Time series plot", dataset, "")
                    "Distribution plots" -> QuickPlotCodeGenerator.generateGuidedTask("Distribution analysis", dataset, "")
                    "String analysis" -> QuickPlotCodeGenerator.generateGuidedTask("Text mining", dataset, "")
                    "Numerical statistics" -> QuickPlotCodeGenerator.generateGuidedTask("Numerical summary", dataset, "")
                    else -> {
                        val toolName = when (selectedTask.title) {
                            "Understand my data" -> "Data overview"
                            "Data wrangling" -> "Clean missing values"
                            "Correlation analysis" -> "Correlation heatmap"
                            "Interactive visualization" -> "Interactive chart"
                            "Date processing" -> "Date helper"
                            "Fast data summary" -> "Fast data summary"
                            "API data import" -> "Import from API"
                            else -> ""
                        }
                        QuickPlotCodeGenerator.generateDataTool(toolName, dataset, dateColumn, apiUrl)
                    }
                }
                onGenerate(code)
            },
            enabled = canRun, modifier = Modifier.fillMaxWidth().height(56.dp), shape = MaterialTheme.shapes.large
        ) { Text("Generate R Code", style = MaterialTheme.typography.titleMedium) }
        Spacer(Modifier.height(48.dp))
    }
}

@Composable
private fun TaskCard(task: DataCoachTask, selected: Boolean, onClick: () -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(task.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text(task.description, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun CoachSectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
}

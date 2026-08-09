package com.jay.droidrstudio

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun QuickPlotWizard(
    onGenerate: (String) -> Unit
) {
    var plotType by remember { mutableStateOf("Histogram") }
    var dataSource by remember { mutableStateOf("Manual") }
    var manualData by remember { mutableStateOf("10, 20, 15, 25, 30, 18, 22") }
    var manualYData by remember { mutableStateOf("12, 18, 14, 28, 27, 20, 25") }
    var title by remember { mutableStateOf("Quick Plot") }
    var xLabel by remember { mutableStateOf("") }
    var yLabel by remember { mutableStateOf("") }
    var color by remember { mutableStateOf("steelblue") }
    var engine by remember { mutableStateOf("ggplot2") }
    var theme by remember { mutableStateOf("Minimal") }
    var format by remember { mutableStateOf("PNG") }
    var palette by remember { mutableStateOf("Standard") }
    var isInteractive by remember { mutableStateOf(false) }
    var alpha by remember { mutableFloatStateOf(1.0f) }
    
    val plotTypes = listOf("Histogram", "Scatter Plot", "Line Plot", "Boxplot")
    val engines = listOf("ggplot2", "Base R")
    val themes = listOf("Minimal", "Classic", "BW", "Gray")
    val formats = listOf("PNG", "PDF")
    val palettes = listOf("Standard", "Viridis", "Magma", "Plasma", "Cividis")
    val colors = listOf("steelblue", "darkred", "forestgreen", "orange", "purple")
    val validationError = QuickPlotCodeGenerator.validationError(plotType, dataSource, manualData, manualYData)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            "Professional Plot Suite",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(24.dp))

        // 1. Engine and Format
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                SectionTitle("Engine")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    engines.forEach { e ->
                        FilterChip(
                            selected = engine == e,
                            onClick = { engine = e },
                            label = { Text(e) }
                        )
                    }
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                SectionTitle("Format")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isInteractive, onCheckedChange = { isInteractive = it })
                    Text("Interactive (Plotly)", style = MaterialTheme.typography.bodySmall)
                }
                if (!isInteractive) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        formats.forEach { f ->
                            FilterChip(
                                selected = format == f,
                                onClick = { format = f },
                                label = { Text(f) }
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

        // 2. Plot Type
        SectionTitle("Select Plot Type")
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            plotTypes.forEach { type ->
                FilterChip(
                    selected = plotType == type,
                    onClick = { plotType = type },
                    label = { Text(type) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }

        if (engine == "ggplot2" || isInteractive) {
            Spacer(modifier = Modifier.height(8.dp))
            SectionTitle("Visual Options")
            Text("Theme", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                themes.forEach { t ->
                    FilterChip(selected = theme == t, onClick = { theme = t }, label = { Text(t) })
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("Color Palette", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                palettes.forEach { p ->
                    FilterChip(selected = palette == p, onClick = { palette = p }, label = { Text(p) })
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("Point Transparency: ${(alpha * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
            Slider(value = alpha, onValueChange = { alpha = it }, valueRange = 0.1f..1.0f)
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

        // 3. Data Source
        SectionTitle("Choose Data Source")
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = dataSource == "Manual", onClick = { dataSource = "Manual" })
            Text("Manual Input", style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.width(24.dp))
            RadioButton(selected = dataSource == "Built-in", onClick = { dataSource = "Built-in" })
            Text("mtcars", style = MaterialTheme.typography.bodyLarge)
        }

        if (dataSource == "Manual") {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = manualData,
                onValueChange = { manualData = it },
                label = { Text(if (plotType == "Scatter Plot") "X values" else "Values") },
                modifier = Modifier.fillMaxWidth(),
                isError = validationError != null,
                placeholder = { Text("Enter numbers...") },
                shape = MaterialTheme.shapes.medium
            )
            if (plotType == "Scatter Plot") {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = manualYData,
                    onValueChange = { manualYData = it },
                    label = { Text("Y values (same number as X values)") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = validationError != null,
                    placeholder = { Text("Enter Y values...") },
                    shape = MaterialTheme.shapes.medium
                )
            }
            validationError?.let { message ->
                Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

        // 4. Details & Aesthetics
        SectionTitle("Plot Details")
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Main Title") },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            singleLine = true,
            shape = MaterialTheme.shapes.medium
        )
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = xLabel,
                onValueChange = { xLabel = it },
                label = { Text("X-Axis Label") },
                modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                singleLine = true,
                shape = MaterialTheme.shapes.medium
            )
            OutlinedTextField(
                value = yLabel,
                onValueChange = { yLabel = it },
                label = { Text("Y-Axis Label") },
                modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                singleLine = true,
                shape = MaterialTheme.shapes.medium
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Color
        if (palette == "Standard") {
            Text("Select Accent Color", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                colors.forEach { col ->
                    FilterChip(
                        selected = color == col,
                        onClick = { color = col },
                        label = { Text(col) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                val extension = if (isInteractive) "html" else if (format == "PDF") "pdf" else "png"
                val fileName = "plot_${System.currentTimeMillis()}.$extension"
                val code = QuickPlotCodeGenerator.generate(
                    type = plotType,
                    source = dataSource,
                    values = manualData,
                    yValues = manualYData,
                    title = title,
                    color = color,
                    fileName = fileName,
                    engine = engine,
                    theme = theme,
                    xLabel = xLabel,
                    yLabel = yLabel,
                    palette = palette,
                    isInteractive = isInteractive,
                    alpha = alpha
                )
                onGenerate(code)
            },
            enabled = validationError == null,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Text(if (isInteractive) "Create Interactive Plot" else "Create Professional Plot", style = MaterialTheme.typography.titleMedium)
        }
        
        Spacer(modifier = Modifier.height(48.dp)) // Extra space for bottom sheet
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

package com.jay.droidrstudio

/** Builds safe, self-contained R scripts using modern component packages. */
object QuickPlotCodeGenerator {
    private val rName = Regex("[A-Za-z.][A-Za-z0-9._]*")

    fun parseValues(input: String): List<Double>? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        val values = trimmed.split(Regex("[,\\s]+")).map { it.toDoubleOrNull() ?: return null }
        return values.takeIf { values.all(Double::isFinite) }
    }

    fun isSafeDatasetName(value: String): Boolean = rName.matches(value)

    fun validationError(type: String, source: String, values: String, yValues: String): String? {
        if (source != "Manual") return null
        val x = parseValues(values) ?: return "Enter comma- or space-separated finite numbers."
        if (type == "Histogram" && x.size < 2) return "A histogram needs at least two values."
        if (type == "Scatter Plot") {
            val y = parseValues(yValues) ?: return "Enter Y values for the scatter plot."
            if (x.size != y.size) return "X and Y must contain the same number of values."
        }
        return null
    }

    fun dataToolValidationError(tool: String, dataset: String, dateColumn: String, apiUrl: String): String? {
        if (tool == "Import from API") {
            return if (apiUrl.startsWith("https://")) null else "Enter an HTTPS API URL."
        }
        if (!isSafeDatasetName(dataset)) return "Use a dataset name such as mtcars or my_data."
        if (tool == "Date helper" && !isSafeDatasetName(dateColumn)) {
            return "Enter the date column name, for example order_date."
        }
        return null
    }

    fun generate(
        type: String, source: String, values: String, yValues: String, title: String, color: String,
        fileName: String, engine: String = "ggplot2", theme: String = "minimal", 
        xLabel: String = "", yLabel: String = "", palette: String = "Standard",
        isInteractive: Boolean = false, alpha: Float = 1.0f
    ): String {
        require(validationError(type, source, values, yValues) == null)
        val safeTitle = rString(title.ifBlank { "Quick Plot" })
        val safeColor = rString(color.ifBlank { "steelblue" })
        val outputFile = rString("/root/$fileName")
        
        return buildString {
            if (isInteractive) {
                append(packageSetup("plotly"))
                append(packageSetup("ggplot2"))
                append(packageSetup("htmlwidgets"))
            } else {
                val device = if (fileName.substringAfterLast(".").lowercase() == "pdf") {
                    "pdf($outputFile, width=10, height=7)"
                } else "png($outputFile, width=1200, height=800, res=144)"
                appendLine(device)
            }

            if (engine == "ggplot2" || isInteractive) {
                if (!isInteractive) append(packageSetup("ggplot2"))
                append(packageSetup("viridis"))
                append(packageSetup("scales"))
                
                if (source == "Manual") {
                    val x = parseValues(values)!!.joinToString(", ")
                    if (type == "Scatter Plot") {
                        val y = parseValues(yValues)!!.joinToString(", ")
                        appendLine("df <- data.frame(x = c($x), y = c($y))")
                        appendLine("p <- ggplot(df, aes(x=x, y=y)) + geom_point(color=$safeColor, size=3, alpha=$alpha)")
                    } else {
                        appendLine("df <- data.frame(val = c($x))")
                        appendLine(ggPlot(type, "df", safeColor, alpha))
                    }
                } else {
                    appendLine(ggPlot(type, "mtcars", safeColor, alpha))
                }
                
                val rXLabel = if (xLabel.isNotBlank()) rString(xLabel) else "NULL"
                val rYLabel = if (yLabel.isNotBlank()) rString(yLabel) else "NULL"
                appendLine("p <- p + labs(title=$safeTitle, x=$rXLabel, y=$rYLabel)")
                
                appendLine(when (theme.lowercase()) {
                    "classic" -> "p <- p + theme_classic()"
                    "bw" -> "p <- p + theme_bw()"
                    "gray" -> "p <- p + theme_gray()"
                    else -> "p <- p + theme_minimal()"
                })

                if (palette != "Standard") {
                    val opt = palette.lowercase()
                    appendLine("p <- p + scale_color_viridis_c(option='$opt') + scale_fill_viridis_c(option='$opt')")
                }

                if (isInteractive) {
                    appendLine("chart <- ggplotly(p)")
                    appendLine("saveWidget(chart, $outputFile, selfcontained = FALSE)")
                } else {
                    appendLine("print(p)")
                }
            } else {
                appendLine("par(bg = 'white', mar = c(4.5, 4.5, 3.5, 1), family='sans')")
                val rXLabel = if (xLabel.isNotBlank()) rString(xLabel) else "NULL"
                val rYLabel = if (yLabel.isNotBlank()) rString(yLabel) else "NULL"
                if (source == "Manual") {
                    appendLine("x <- c(${parseValues(values)!!.joinToString(", ")})")
                    if (type == "Scatter Plot") appendLine("y <- c(${parseValues(yValues)!!.joinToString(", ")})")
                    appendManualPlot(this, type, safeTitle, safeColor, rXLabel, rYLabel)
                } else {
                    appendMtcarsPlot(this, type, safeTitle, safeColor, rXLabel, rYLabel)
                }
            }
            if (!isInteractive) appendLine("dev.off()")
        }
    }

    /** Scripts for the Data Science Tools gallery. */
    fun generateDataTool(tool: String, dataset: String, dateColumn: String = "", apiUrl: String = ""): String {
        require(dataToolValidationError(tool, dataset, dateColumn, apiUrl) == null)
        val data = dataset.ifBlank { "mtcars" }
        return when (tool) {
            "Data overview" -> buildString {
                append(packageSetup("cli")); append(packageSetup("psych"))
                appendLine("cli_h1('Dataset Overview: $data')")
                appendLine("cli_alert_info('Rows: {nrow($data)}, Columns: {ncol($data)}')")
                appendLine("cli_divider()")
                appendLine("print(describe($data))")
            }
            "Correlation heatmap" -> buildString {
                append(packageSetup("corrplot")); append(packageSetup("cli")); append(packageSetup("viridis"))
                appendLine("cli_h1('Correlation Analysis')")
                appendLine("png('/root/corr_plot.png', width=1000, height=1000, res=144)")
                appendLine("numeric_data <- Filter(is.numeric, $data)")
                appendLine("if (ncol(numeric_data) < 2) { cli_alert_danger('Need 2+ numeric columns'); stop() }")
                appendLine("corrplot(cor(numeric_data, use='pairwise.complete.obs'), method='color', type='upper', order='hclust', tl.col='black', tl.srt=45, col=viridis(200))")
                appendLine("dev.off()")
                appendLine("cli_alert_success('Heatmap saved to /root/corr_plot.png')")
            }
            "Interactive chart" -> buildString {
                append(packageSetup("plotly")); append(packageSetup("cli")); append(packageSetup("htmlwidgets"))
                appendLine("cli_h1('Creating Interactive Plotly Chart')")
                appendLine("numeric_data <- Filter(is.numeric, $data)")
                appendLine("if (length(numeric_data) < 2) { cli_alert_danger('Need 2+ numeric columns'); stop() }")
                appendLine("chart <- plot_ly($data, x = ~numeric_data[[1]], y = ~numeric_data[[2]], type = 'scatter', mode = 'markers', marker = list(size = 8, opacity = 0.7)) |>")
                appendLine("  layout(title = 'Interactive Scatter Plot', hovermode = 'closest')")
                appendLine("saveWidget(chart, '/root/interactive_chart.html', selfcontained = FALSE)")
                appendLine("cli_alert_success('Chart saved to /root/interactive_chart.html')")
            }
            "Clean missing values" -> buildString {
                append(packageSetup("cli")); append(packageSetup("dplyr")); append(packageSetup("tidyr"))
                appendLine("cli_h1('Data Cleaning')")
                appendLine("cli_alert_info('Removing missing values and duplicates...')")
                appendLine("original_rows <- nrow($data)")
                appendLine("clean_data <- $data |> drop_na() |> distinct()")
                appendLine("removed <- original_rows - nrow(clean_data)")
                appendLine("cli_alert_success('Removed {removed} rows with missing/duplicate values')")
                appendLine("cli_text('Remaining: {nrow(clean_data)} rows × {ncol(clean_data)} columns')")
                appendLine("print(glimpse(clean_data))")
            }
            "Fast data summary" -> buildString {
                append(packageSetup("cli")); append(packageSetup("data.table"))
                appendLine("cli_h1('High-Speed Summary (data.table)')")
                appendLine("cli_text('Computing statistics with data.table...')")
                appendLine("dt <- as.data.table($data)")
                appendLine("summary_stats <- dt[, lapply(.SD, function(x) if (is.numeric(x)) list(mean=mean(x, na.rm=TRUE), sd=sd(x, na.rm=TRUE)) else list(unique=uniqueN(x)))]")
                appendLine("cli_alert_success('Summary computed')")
                appendLine("print(summary_stats)")
            }
            "Date helper" -> buildString {
                append(packageSetup("cli")); append(packageSetup("lubridate")); append(packageSetup("dplyr"))
                appendLine("cli_h1('Date Parsing & Processing')")
                appendLine("cli_text('Parsing dates from column: {$dateColumn}')")
                appendLine("processed_data <- $data |> mutate(parsed_date = ymd(!!as.symbol($dateColumn)))")
                appendLine("cli_alert_success('Successfully parsed {sum(!is.na(processed_data\$parsed_date))} dates')")
                appendLine("print(utils::head(processed_data, 10))")
            }
            "Import from API" -> buildString {
                append(packageSetup("cli")); append(packageSetup("jsonlite"))
                appendLine("cli_h1('Web Data Import from API')")
                appendLine("cli_text('Downloading from: ${rString(apiUrl)}')")
                appendLine("tryCatch({")
                appendLine("  json_text <- readLines(${rString(apiUrl)}, warn = FALSE)")
                appendLine("  api_data <- fromJSON(paste(json_text, collapse = ''), flatten = TRUE)")
                appendLine("  if (is.data.frame(api_data)) {")
                appendLine("    cli_alert_success('Imported {nrow(api_data)} rows × {ncol(api_data)} columns')")
                appendLine("  } else {")
                appendLine("    cli_alert_info('Data structure: {typeof(api_data)}')")
                appendLine("  }")
                appendLine("  print(utils::head(api_data, 10))")
                appendLine("}, error = function(e) {")
                appendLine("  cli_alert_danger('Error: {e\$message}')")
                appendLine("})")
            }
            "Color Analysis" -> buildString {
                append(packageSetup("cli")); append(packageSetup("farver")); append(packageSetup("data.table"))
                appendLine("cli_h1('Advanced Color Space Analysis (farver)')")
                appendLine("cli_text('Named color conversion and perceptual distance')")
                appendLine("colors_named <- c('red', 'green', 'blue', 'yellow', 'purple', 'orange')")
                appendLine("lab_colors <- decode_colour(colors_named, from = 'css', to = 'lab')")
                appendLine("dt_colors <- as.data.table(cbind(color = colors_named, lab_colors))")
                appendLine("cli_alert_success('Converted {length(colors_named)} colors to Lab space')")
                appendLine("print(dt_colors)")
                appendLine("cli_h2('Color Distances')")
                appendLine("distances <- compare_colour(lab_colors[1,], lab_colors, from_space = 'lab')")
                appendLine("cat('Distance from red to other colors:\\\\n')")
                appendLine("print(data.frame(color = colors_named, distance = distances))")
            }
            "Export JSON" -> buildString {
                append(packageSetup("jsonlite")); append(packageSetup("cli"))
                appendLine("cli_h1('Export Dataset to JSON')")
                appendLine("cli_text('Processing {nrow($data)} rows...')")
                appendLine("export_data <- utils::head($data, min(100, nrow($data)))")
                appendLine("write_json(export_data, '/root/data_export.json', pretty = TRUE, na = 'null')")
                appendLine("cli_alert_success('Exported {nrow(export_data)} rows to /root/data_export.json')")
            }
            "String analysis" -> buildString {
                append(packageSetup("cli")); append(packageSetup("stringr")); append(packageSetup("dplyr"))
                appendLine("cli_h1('String Pattern Analysis')")
                appendLine("char_cols <- Filter(is.character, $data)")
                appendLine("if (length(char_cols) == 0) { cli_alert_danger('No character columns found'); stop() }")
                appendLine("col_name <- names(char_cols)[1]")
                appendLine("cli_text('Analyzing column: {col_name}')")
                appendLine("text_data <- char_cols[[1]]")
                appendLine("lengths <- str_length(text_data)")
                appendLine("cli_alert_info('Mean length: {round(mean(lengths, na.rm=TRUE), 2)} chars')")
                appendLine("cli_alert_info('Max length: {max(lengths, na.rm=TRUE)} chars')")
            }
            "Numerical summary" -> buildString {
                append(packageSetup("cli")); append(packageSetup("psych")); append(packageSetup("dplyr"))
                appendLine("cli_h1('Detailed Numerical Summary')")
                appendLine("numeric_cols <- $data |> select(where(is.numeric))")
                appendLine("if (ncol(numeric_cols) == 0) { cli_alert_danger('No numeric columns'); stop() }")
                appendLine("cli_text('Analyzing {ncol(numeric_cols)} numeric columns...')")
                appendLine("summary_table <- describe(numeric_cols)")
                appendLine("cli_alert_success('Summary computed')")
                appendLine("print(summary_table)")
            }
            else -> error("Unknown data tool: $tool")
        }
    }

    /** Workflow tasks for the Data Coach. */
    fun generateGuidedTask(task: String, dataset: String, apiUrl: String = ""): String {
        require(isSafeDatasetName(dataset))
        val script = when (task) {
            "Understand my data" -> buildString {
                append(packageSetup("cli")); append(packageSetup("data.table")); append(packageSetup("psych"))
                appendLine("cli_h1('Data Coach: Initial Discovery')")
                appendLine("dt <- as.data.table($dataset)")
                appendLine("cli_alert_info('Shape: {nrow(dt)} rows × {ncol(dt)} columns')")
                appendLine("cli_divider()")
                appendLine("print(describe(Filter(is.numeric, dt)))")
            }
            "Clean and save it" -> buildString {
                append(packageSetup("cli")); append(packageSetup("dplyr")); append(packageSetup("tidyr"))
                appendLine("cli_h1('Data Coach: Cleaning Pipeline')")
                appendLine("cli_text('Removing missing values and duplicates...')")
                appendLine("clean_data <- $dataset |> drop_na() |> distinct()")
                appendLine("cli_alert_success('Cleaned: {nrow($dataset)} → {nrow(clean_data)} rows')")
                appendLine("print(glimpse(clean_data))")
            }
            "Make an interactive chart" -> buildString {
                append(packageSetup("plotly")); append(packageSetup("cli")); append(packageSetup("dplyr")); append(packageSetup("htmlwidgets"))
                appendLine("cli_h1('Interactive Visualization')")
                appendLine("numeric_data <- $dataset |> select(where(is.numeric))")
                appendLine("if (ncol(numeric_data) < 2) { cli_alert_danger('Need 2+ numeric columns'); stop() }")
                appendLine("chart <- plot_ly($dataset, x = ~numeric_data[[1]], y = ~numeric_data[[2]],")
                appendLine("                          type = 'scatter', mode = 'markers',")
                appendLine("                          marker = list(size = 8, opacity = 0.6)) |>")
                appendLine("  layout(title = 'Interactive Data Explorer', hovermode = 'closest')")
                appendLine("saveWidget(chart, '/root/my_chart.html', selfcontained = FALSE)")
                appendLine("cli_alert_success('Interactive chart generated: /root/my_chart.html')")
            }
            "Get JSON from the web" -> {
                require(apiUrl.startsWith("https://"))
                buildString {
                    append(packageSetup("cli")); append(packageSetup("jsonlite"))
                    appendLine("cli_rule('Web Data Fetcher')")
                    appendLine("cli_text('Downloading from API...')")
                    appendLine("tryCatch({")
                    appendLine("  json_text <- readLines(${rString(apiUrl)}, warn = FALSE)")
                    appendLine("  web_data <- fromJSON(paste(json_text, collapse = ''), flatten = TRUE)")
                    appendLine("  item_count <- if (is.data.frame(web_data)) nrow(web_data) else length(web_data)")
                    appendLine("  cli_alert_success('Loaded {item_count} items from API')")
                    appendLine("  print(utils::head(web_data, 10))")
                    appendLine("}, error = function(e) {")
                    appendLine("  cli_alert_danger('Error: {e\$message}')")
                    appendLine("})")
                }
            }
            "Smart Import (readr)" -> buildString {
                append(packageSetup("cli")); append(packageSetup("readr")); append(packageSetup("dplyr"))
                appendLine("cli_h1('Smart CSV Import')")
                appendLine("my_data <- read_csv('/root/imported_data.csv')")
                appendLine("cli_alert_success('Data loaded into variable \"my_data\"')")
                appendLine("cli_text('{nrow(my_data)} rows, {ncol(my_data)} columns')")
                appendLine("print(glimpse(my_data))")
            }
            "Color Analysis" -> buildString {
                append(packageSetup("cli")); append(packageSetup("farver")); append(packageSetup("data.table"))
                appendLine("cli_h1('Color Space Analysis')")
                appendLine("cli_alert_info('Converting standard colors to multiple color spaces')")
                appendLine("colors_list <- c('red', 'green', 'blue', 'yellow', 'purple', 'orange')")
                appendLine("hcl_colors <- decode_colour(colors_list, from = 'css', to = 'hcl')")
                appendLine("lab_colors <- decode_colour(colors_list, from = 'css', to = 'lab')")
                appendLine("color_df <- data.frame(color = colors_list, H = hcl_colors[,1], C = hcl_colors[,2], L = hcl_colors[,3])")
                appendLine("cli_alert_success('Conversion complete')")
                appendLine("print(color_df)")
            }
            "Time series plot" -> buildString {
                append(packageSetup("plotly")); append(packageSetup("cli")); append(packageSetup("lubridate")); append(packageSetup("htmlwidgets"))
                appendLine("cli_h1('Time Series Visualization')")
                appendLine("if (!('time' %in% names($dataset))) { cli_alert_danger('No time column found'); stop() }")
                appendLine("data_ts <- $dataset")
                appendLine("chart <- plot_ly(data_ts, x = ~time, y = ~value, type = 'scatter', mode = 'lines+markers') |>")
                appendLine("  layout(title = 'Time Series', xaxis = list(title = 'Time'), yaxis = list(title = 'Value'))")
                appendLine("saveWidget(chart, '/root/timeseries.html', selfcontained = FALSE)")
                appendLine("cli_alert_success('Time series chart saved')")
            }
            "Distribution analysis" -> buildString {
                append(packageSetup("cli")); append(packageSetup("plotly")); append(packageSetup("dplyr")); append(packageSetup("htmlwidgets"))
                appendLine("cli_h1('Distribution Explorer')")
                appendLine("numeric_cols <- $dataset |> select(where(is.numeric))")
                appendLine("if (ncol(numeric_cols) == 0) { cli_alert_danger('No numeric columns'); stop() }")
                appendLine("first_col <- numeric_cols[[1]]")
                appendLine("chart <- plot_ly(x = ~first_col, type = 'histogram', nbinsx = 30) |>")
                appendLine("  layout(title = 'Distribution', xaxis = list(title = names(numeric_cols)[1]))")
                appendLine("saveWidget(chart, '/root/distribution.html', selfcontained = FALSE)")
                appendLine("cli_alert_success('Distribution plot saved')")
            }
            "Text mining" -> buildString {
                append(packageSetup("cli")); append(packageSetup("stringr")); append(packageSetup("dplyr"))
                appendLine("cli_h1('Text Analysis')")
                appendLine("char_data <- $dataset |> select(where(is.character))")
                appendLine("if (ncol(char_data) == 0) { cli_alert_danger('No text columns'); stop() }")
                appendLine("text_col <- char_data[[1]]")
                appendLine("word_counts <- str_count(text_col, '\\\\b\\\\w+\\\\b')")
                appendLine("cli_h2('Word Count Statistics')")
                appendLine("cli_alert_info('Mean: {round(mean(word_counts, na.rm=TRUE), 2)} words')")
                appendLine("cli_alert_info('Max: {max(word_counts, na.rm=TRUE)} words')")
                appendLine("cli_alert_info('Min: {min(word_counts, na.rm=TRUE)} words')")
            }
            else -> error("Unknown guided task: $task")
        }
        val importedDataPreamble = if (dataset == "my_data") {
            "my_data <- utils::read.csv('/root/imported_data.csv')\n"
        } else ""
        return importedDataPreamble + script
    }

    private fun packageSetup(packageName: String) = """
        if (!requireNamespace('$packageName', quietly = TRUE)) {
          install.packages('$packageName', repos = 'https://cloud.r-project.org/')
        }
        library($packageName)
    """.trimIndent() + "\n"

    private fun ggPlot(type: String, data: String, color: String, alpha: Float) = when (type) {
        "Histogram" -> "p <- ggplot($data, aes(x=${if (data == "df") "val" else "mpg"})) + geom_histogram(fill=$color, color='white', bins=15, alpha=$alpha)"
        "Scatter Plot" -> "p <- ggplot($data, aes(x=hp, y=mpg)) + geom_point(color=$color, size=3, alpha=$alpha)"
        "Line Plot" -> "p <- ggplot($data, aes(x=seq_len(nrow($data)), y=${if (data == "df") "val" else "mpg"})) + geom_line(color=$color, alpha=$alpha) + geom_point(color=$color, alpha=$alpha)"
        else -> if (data == "df") {
            "p <- ggplot(df, aes(y=val)) + geom_boxplot(fill=$color, alpha=$alpha)"
        } else {
            "p <- ggplot(mtcars, aes(x=factor(cyl), y=mpg)) + geom_boxplot(fill=$color, alpha=$alpha)"
        }
    }

    private fun appendManualPlot(code: StringBuilder, type: String, title: String, color: String, xLabel: String, yLabel: String) {
        when (type) {
            "Histogram" -> code.appendLine("hist(x, main=$title, xlab=ifelse(is.null($xLabel), 'Value', $xLabel), col=$color, border='white')")
            "Scatter Plot" -> code.appendLine("plot(x, y, main=$title, xlab=ifelse(is.null($xLabel), 'X', $xLabel), ylab=ifelse(is.null($yLabel), 'Y', $yLabel), col=$color, pch=16)")
            "Line Plot" -> code.appendLine("plot(seq_along(x), x, type='o', main=$title, xlab=ifelse(is.null($xLabel), 'Observation', $xLabel), ylab=ifelse(is.null($yLabel), 'Value', $yLabel), col=$color, pch=16, lwd=2)")
            else -> code.appendLine("boxplot(x, main=$title, ylab=ifelse(is.null($yLabel), 'Value', $yLabel), col=$color)")
        }
    }

    private fun appendMtcarsPlot(code: StringBuilder, type: String, title: String, color: String, xLabel: String, yLabel: String) {
        when (type) {
            "Histogram" -> code.appendLine("hist(mtcars\$mpg, main=$title, xlab=ifelse(is.null($xLabel), 'Miles per gallon', $xLabel), col=$color, border='white')")
            "Scatter Plot" -> code.appendLine("plot(mtcars\$hp, mtcars\$mpg, main=$title, xlab=ifelse(is.null($xLabel), 'Horsepower', $xLabel), ylab=ifelse(is.null($yLabel), 'Miles per gallon', $yLabel), col=$color, pch=16)")
            "Line Plot" -> code.appendLine("plot(seq_len(nrow(mtcars)), mtcars\$mpg, type='o', main=$title, xlab=ifelse(is.null($xLabel), 'Car index', $xLabel), ylab=ifelse(is.null($yLabel), 'Miles per gallon', $yLabel), col=$color, pch=16, lwd=2)")
            else -> code.appendLine("boxplot(mpg ~ cyl, data=mtcars, main=$title, xlab=ifelse(is.null($xLabel), 'Cylinders', $xLabel), ylab=ifelse(is.null($yLabel), 'Miles per gallon', $yLabel), col=$color)")
        }
    }

    private fun rString(value: String): String = "'" + value.replace("\\", "\\\\").replace("'", "\\'").replace("\n", " ").replace("\r", " ") + "'"
}

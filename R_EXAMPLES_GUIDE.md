# DroidRStudio - Alpine R Package Guide & Examples

## Available R Packages on Alpine Filesystem

The Alpine filesystem contains these professional R packages:
- **Data Manipulation**: `dplyr`, `tidyr`, `data.table`
- **Data Import**: `readr`, `jsonlite`
- **Visualization**: `ggplot2`, `plotly`, `viridis`, `corrplot`, `scales`
- **Text Processing**: `stringr`
- **Date/Time**: `lubridate`
- **Statistics**: `psych`
- **Colors**: `farver`
- **Terminal UI**: `cli`
- **Programming**: `Rcpp`

---

## 1. Data Import & Exploration with `readr` & `cli`

### Smart CSV Import with Type Detection
```r
if (!requireNamespace('readr', quietly = TRUE)) {
  install.packages('readr', repos = 'https://cloud.r-project.org/')
}
if (!requireNamespace('cli', quietly = TRUE)) {
  install.packages('cli', repos = 'https://cloud.r-project.org/')
}

cli::cli_h1('Smart Data Import')
my_data <- readr::read_csv('/root/data.csv')
cli::cli_alert_success('Loaded {nrow(my_data)} rows × {ncol(my_data)} columns')
print(readr::spec(my_data))
```

### JSON Data Processing
```r
if (!requireNamespace('jsonlite', quietly = TRUE)) {
  install.packages('jsonlite', repos = 'https://cloud.r-project.org/')
}
if (!requireNamespace('cli', quietly = TRUE)) {
  install.packages('cli', repos = 'https://cloud.r-project.org/')
}

# Download JSON from API (no httr - use readLines)
cli::cli_h1('JSON Data Import')
json_text <- readLines('https://jsonplaceholder.typicode.com/posts', warn = FALSE)
data <- jsonlite::fromJSON(paste(json_text, collapse = ''), flatten = TRUE)
cli::cli_alert_success('Imported {nrow(data)} items')

# Export to JSON
jsonlite::write_json(utils::head(data, 50), '/root/posts.json', pretty = TRUE)
cli::cli_alert_success('Saved to /root/posts.json')
```

---

## 2. Data Cleaning with `dplyr` & `tidyr`

### Modern Data Wrangling Pipeline
```r
if (!requireNamespace('dplyr', quietly = TRUE)) {
  install.packages('dplyr', repos = 'https://cloud.r-project.org/')
}
if (!requireNamespace('tidyr', quietly = TRUE)) {
  install.packages('tidyr', repos = 'https://cloud.r-project.org/')
}
if (!requireNamespace('cli', quietly = TRUE)) {
  install.packages('cli', repos = 'https://cloud.r-project.org/')
}

cli::cli_h1('Data Cleaning Pipeline')

# Start with mtcars
clean_data <- mtcars |>
  dplyr::mutate(
    brand = stringr::str_to_title(rownames(mtcars)),
    mpg_category = dplyr::case_when(
      mpg < 15 ~ 'Low',
      mpg < 25 ~ 'Medium',
      TRUE ~ 'High'
    )
  ) |>
  dplyr::filter(hp > 50) |>
  dplyr::distinct() |>
  tidyr::drop_na()

cli::cli_alert_success('Cleaned {nrow(clean_data)} rows')
print(head(clean_data, 10))
```

### String Manipulation with `stringr`
```r
if (!requireNamespace('stringr', quietly = TRUE)) {
  install.packages('stringr', repos = 'https://cloud.r-project.org/')
}
if (!requireNamespace('dplyr', quietly = TRUE)) {
  install.packages('dplyr', repos = 'https://cloud.r-project.org/')
}
if (!requireNamespace('cli', quietly = TRUE)) {
  install.packages('cli', repos = 'https://cloud.r-project.org/')
}

cli::cli_h1('String Analysis')

text_samples <- c(
  'The quick brown fox jumps over the lazy dog',
  'R is a powerful language for data science',
  'dplyr makes data wrangling a breeze'
)

analysis <- data.frame(
  text = text_samples,
  length = stringr::str_length(text_samples),
  word_count = stringr::str_count(text_samples, '\\b\\w+\\b'),
  contains_r = stringr::str_detect(text_samples, '[Rr]')
)

cli::cli_alert_info('Analyzed {length(text_samples)} text samples')
print(analysis)
```

---

## 3. Interactive Visualization with `plotly` & `ggplot2`

### Plotly Interactive Scatter Plot
```r
if (!requireNamespace('plotly', quietly = TRUE)) {
  install.packages('plotly', repos = 'https://cloud.r-project.org/')
}
if (!requireNamespace('cli', quietly = TRUE)) {
  install.packages('cli', quietly = TRUE)
}

cli::cli_h1('Interactive Plotly Visualization')

chart <- plotly::plot_ly(
  mtcars,
  x = ~hp,
  y = ~mpg,
  color = ~cyl,
  size = ~wt,
  type = 'scatter',
  mode = 'markers',
  marker = list(sizemode = 'diameter', opacity = 0.7)
) %>%
  plotly::layout(
    title = 'Car Performance Analysis (Interactive)',
    xaxis = list(title = 'Horsepower'),
    yaxis = list(title = 'Miles per Gallon'),
    hovermode = 'closest'
  )

htmlwidgets::saveWidget(chart, '/root/scatter_plot.html', selfcontained = FALSE)
cli::cli_alert_success('Interactive chart saved to /root/scatter_plot.html')
```

### ggplot2 with viridis Palette
```r
if (!requireNamespace('ggplot2', quietly = TRUE)) {
  install.packages('ggplot2', repos = 'https://cloud.r-project.org/')
}
if (!requireNamespace('viridis', quietly = TRUE)) {
  install.packages('viridis', repos = 'https://cloud.r-project.org/')
}
if (!requireNamespace('scales', quietly = TRUE)) {
  install.packages('scales', repos = 'https://cloud.r-project.org/')
}

png('/root/ggplot_example.png', width = 1200, height = 800, res = 144)

p <- ggplot(mtcars, aes(x = hp, y = mpg, color = as.factor(cyl), size = wt)) +
  geom_point(alpha = 0.7) +
  scale_color_viridis_d(option = 'turbo') +
  scale_size_continuous(range = c(2, 8)) +
  labs(
    title = 'Car Specifications: Horsepower vs Fuel Efficiency',
    x = 'Horsepower',
    y = 'Miles per Gallon',
    color = 'Cylinders',
    size = 'Weight (1000 lbs)'
  ) +
  theme_minimal() +
  theme(
    plot.title = element_text(face = 'bold', size = 16),
    legend.position = 'right'
  )

print(p)
dev.off()
cli::cli_alert_success('Plot saved to /root/ggplot_example.png')
```

---

## 4. Statistical Analysis with `psych`

### Comprehensive Statistical Summary
```r
if (!requireNamespace('psych', quietly = TRUE)) {
  install.packages('psych', repos = 'https://cloud.r-project.org/')
}
if (!requireNamespace('cli', quietly = TRUE)) {
  install.packages('cli', repos = 'https://cloud.r-project.org/')
}

cli::cli_h1('Descriptive Statistics (psych)')

# Describe numeric variables
numeric_data <- mtcars |> dplyr::select(where(is.numeric))
stats <- psych::describe(numeric_data)

cli::cli_text('Dataset shape: {nrow(mtcars)} rows × {ncol(mtcars)} columns')
cli::cli_divider()
print(stats)

# Correlation analysis
cli::cli_h2('Correlation Matrix')
corr_matrix <- psych::corr.test(numeric_data)
print(corr_matrix$r)
```

---

## 5. Correlation Heatmap with `corrplot`

### Professional Correlation Visualization
```r
if (!requireNamespace('corrplot', quietly = TRUE)) {
  install.packages('corrplot', repos = 'https://cloud.r-project.org/')
}
if (!requireNamespace('viridis', quietly = TRUE)) {
  install.packages('viridis', repos = 'https://cloud.r-project.org/')
}
if (!requireNamespace('cli', quietly = TRUE)) {
  install.packages('cli', repos = 'https://cloud.r-project.org/')
}

cli::cli_h1('Correlation Heatmap')

numeric_cols <- Filter(is.numeric, mtcars)
if (ncol(numeric_cols) >= 2) {
  png('/root/correlation_heatmap.png', width = 1000, height = 1000, res = 144)
  
  corr_matrix <- cor(numeric_cols, use = 'pairwise.complete.obs')
  corrplot::corrplot(
    corr_matrix,
    method = 'color',
    type = 'upper',
    order = 'hclust',
    tl.col = 'black',
    tl.srt = 45,
    col = viridis::viridis(200),
    main = 'Correlation Analysis'
  )
  
  dev.off()
  cli::cli_alert_success('Heatmap saved to /root/correlation_heatmap.png')
}
```

---

## 6. Color Analysis with `farver`

### Color Space Conversion & Distance
```r
if (!requireNamespace('farver', quietly = TRUE)) {
  install.packages('farver', repos = 'https://cloud.r-project.org/')
}
if (!requireNamespace('data.table', quietly = TRUE)) {
  install.packages('data.table', repos = 'https://cloud.r-project.org/')
}
if (!requireNamespace('cli', quietly = TRUE)) {
  install.packages('cli', repos = 'https://cloud.r-project.org/')
}

cli::cli_h1('Advanced Color Space Analysis')

# Define colors
colors_list <- c('red', 'green', 'blue', 'yellow', 'purple', 'orange', 'cyan')

# Convert to different color spaces
rgb_values <- farver::decode_colour(colors_list, from = 'css', to = 'rgb')
lab_values <- farver::decode_colour(colors_list, from = 'css', to = 'lab')
hcl_values <- farver::decode_colour(colors_list, from = 'css', to = 'hcl')

# Create summary table
color_summary <- data.frame(
  Color = colors_list,
  L = lab_values[, 1],
  A = lab_values[, 2],
  B = lab_values[, 3],
  H = hcl_values[, 1],
  C = hcl_values[, 2],
  L_hcl = hcl_values[, 3]
)

cli::cli_alert_success('Color spaces converted')
print(color_summary)

# Compute color distances
cli::cli_h2('Perceptual Distances from Red')
ref_color <- lab_values[1, ]
distances <- farver::compare_colour(ref_color, lab_values, from_space = 'lab')
dist_df <- data.frame(Color = colors_list, Delta_E = distances)
print(dist_df)

cli::cli_alert_info('Colors ranked by distance from red')
```

---

## 7. Date/Time Processing with `lubridate`

### Date Manipulation Pipeline
```r
if (!requireNamespace('lubridate', quietly = TRUE)) {
  install.packages('lubridate', repos = 'https://cloud.r-project.org/')
}
if (!requireNamespace('dplyr', quietly = TRUE)) {
  install.packages('dplyr', repos = 'https://cloud.r-project.org/')
}
if (!requireNamespace('cli', quietly = TRUE)) {
  install.packages('cli', repos = 'https://cloud.r-project.org/')
}

cli::cli_h1('Date/Time Processing')

# Create sample dates
dates <- data.frame(
  raw_date = c('2026-01-15', '2026-06-20', '2026-12-25', '2026-03-10')
)

processed <- dates |>
  dplyr::mutate(
    date = lubridate::ymd(raw_date),
    year = lubridate::year(date),
    month = lubridate::month(date),
    week = lubridate::week(date),
    day_of_year = lubridate::yday(date),
    days_from_now = as.numeric(date - lubridate::today())
  )

cli::cli_alert_success('Dates processed successfully')
print(processed)
```

---

## 8. High-Performance Data Processing with `data.table`

### Fast Aggregation & Filtering
```r
if (!requireNamespace('data.table', quietly = TRUE)) {
  install.packages('data.table', repos = 'https://cloud.r-project.org/')
}
if (!requireNamespace('cli', quietly = TRUE)) {
  install.packages('cli', repos = 'https://cloud.r-project.org/')
}

cli::cli_h1('High-Performance Data Processing (data.table)')

# Convert to data.table
dt <- data.table::as.data.table(mtcars)

# Group-wise operations
cli::cli_h2('Summary by Cylinder Count')
summary_by_cyl <- dt[, .(
  mean_mpg = mean(mpg),
  mean_hp = mean(hp),
  count = .N,
  total_wt = sum(wt)
), by = cyl]

print(summary_by_cyl)

# Complex aggregation
cli::cli_h2('Multi-level Aggregation')
dt[, cyl_group := factor(cyl)][, .(
  avg_mpg = mean(mpg),
  max_hp = max(hp),
  vehicles = .N
), by = cyl_group]
```

---

## 9. Terminal Output with `cli`

### Professional Console Output
```r
if (!requireNamespace('cli', quietly = TRUE)) {
  install.packages('cli', repos = 'https://cloud.r-project.org/')
}

cli::cli_h1('CLI Output Examples')
cli::cli_h2('Alert Types')

cli::cli_alert_success('Operation completed successfully!')
cli::cli_alert_warning('This is a warning message')
cli::cli_alert_danger('This is an error message')
cli::cli_alert_info('This is an informational message')

cli::cli_divider()
cli::cli_h2('Formatted Output')

cli::cli_text('Variable values: x = {123}, y = {456}, result = {123 + 456}')

cli::cli_rule('Section Header')

# Bullet points
cli::cli_bullets(c('✓' = 'First item', '✓' = 'Second item', 'ℹ' = 'Third item'))

cli::cli_divider()
cli::cli_alert_success('Demo complete!')
```

---

## 10. Advanced Combined Workflow

### End-to-End Data Analysis Pipeline
```r
# Load all required packages
packages <- c('readr', 'dplyr', 'tidyr', 'stringr', 'ggplot2', 
              'plotly', 'corrplot', 'psych', 'data.table', 'cli')

for (pkg in packages) {
  if (!requireNamespace(pkg, quietly = TRUE)) {
    install.packages(pkg, repos = 'https://cloud.r-project.org/')
  }
}

cli::cli_h1('Complete Data Analysis Workflow')

# 1. Load data
cli::cli_h2('Step 1: Data Import')
data <- mtcars
cli::cli_alert_success('Loaded {nrow(data)} rows')

# 2. Clean data
cli::cli_h2('Step 2: Data Cleaning')
clean_data <- data |>
  dplyr::mutate(brand = stringr::str_to_title(rownames(data))) |>
  tidyr::drop_na() |>
  dplyr::distinct()
cli::cli_alert_success('Cleaned to {nrow(clean_data)} rows')

# 3. Statistical summary
cli::cli_h2('Step 3: Statistical Analysis')
stats <- psych::describe(dplyr::select(clean_data, where(is.numeric)))
cli::cli_alert_success('Statistics computed')

# 4. Create visualization
cli::cli_h2('Step 4: Visualization')
p <- ggplot(clean_data, aes(x = hp, y = mpg, color = as.factor(cyl))) +
  geom_point(size = 3, alpha = 0.7) +
  labs(title = 'Analysis Results', x = 'Horsepower', y = 'MPG') +
  theme_minimal()

chart <- plotly::ggplotly(p)
htmlwidgets::saveWidget(chart, '/root/analysis.html', selfcontained = FALSE)
cli::cli_alert_success('Chart saved to /root/analysis.html')

cli::cli_rule('Analysis Complete')
```

---

## Package Alternatives & Use Cases

| Task | Recommended Package | Alternative |
|------|-------------------|-------------|
| CSV Import | `readr` | base `read.csv()` |
| Data Wrangling | `dplyr` | base `subset()`, `aggregate()` |
| Reshaping | `tidyr` | base `reshape()` |
| Text Processing | `stringr` | base `paste()`, `gsub()` |
| Static Plots | `ggplot2` | base `plot()` |
| Interactive Plots | `plotly` | N/A |
| Correlation Heatmap | `corrplot` | base `heatmap()` |
| Statistics | `psych` | base `summary()` |
| Fast Data Ops | `data.table` | `dplyr` |
| Colors | `farver` | base color names |
| Terminal UI | `cli` | `cat()`, `message()` |

---

## Tips for Alpine R Environment

1. **No system dependencies**: Alpine is minimal, so most packages install from source
2. **Network access**: Use `readLines()` for simple HTTP requests (no httr)
3. **JSON handling**: Always use `jsonlite` for JSON parsing
4. **File paths**: Use `/root/` for persistent file storage
5. **Memory**: Keep datasets moderate size; Alpine has memory constraints
6. **Performance**: Use `data.table` for large datasets (100k+ rows)
7. **Colors**: Always validate color names with `farver::decode_colour()`

---

## Common Errors & Solutions

**Error**: `Error: object 'x' not found`  
**Solution**: Ensure package is loaded with `library()` or use `package::function()` syntax

**Error**: `readLines() failed`  
**Solution**: Check HTTPS URL and network; use `warn = FALSE` parameter

**Error**: `ggplot2 not installed`  
**Solution**: Run `install.packages('ggplot2', repos = 'https://cloud.r-project.org/')`

**Error**: `htmlwidgets package missing`  
**Solution**: Already available; ensure path permissions for `/root/`

---

## Performance Benchmarks

- **readr**: 5-10x faster than base `read.csv()` for large files
- **data.table**: 10-50x faster than dplyr for aggregations on 1M+ rows
- **plotly**: 100-200ms overhead for interactive rendering
- **ggplot2**: Fast even with 100k points; use alpha for overlapping points

---

Generated: 2026-07-27
DroidRStudio Alpine R Integration Guide

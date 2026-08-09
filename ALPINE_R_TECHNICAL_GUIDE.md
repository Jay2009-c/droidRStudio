# Alpine R on DroidRStudio - Technical Implementation Guide

## Available Packages on Alpine Filesystem

```
✓ ggplot2        - Grammar of Graphics (2D visualization)
✓ dplyr          - Data manipulation (filtering, selecting, mutating)
✓ readr          - Fast CSV/TSV reading with type inference
✓ tidyr          - Data reshaping and cleaning
✓ tibble         - Modern data frames
✓ stringr        - String manipulation and regex
✓ lubridate      - Date/time processing
✓ data.table     - High-performance data operations
✓ psych          - Psychological statistics and data analysis
✓ Rcpp           - C++ integration for performance
✓ jsonlite       - JSON parsing and generation
✓ corrplot       - Correlation matrix visualization
✓ scales         - Scale functions for ggplot2
✓ viridis        - Colorblind-friendly color palettes
✓ plotly         - Interactive web-based graphics
✓ cli            - Terminal output formatting
✓ farver         - Color manipulation and conversion
```

---

## Package Setup Pattern

All generated R scripts use this consistent pattern:

```r
if (!requireNamespace('package_name', quietly = TRUE)) {
  install.packages('package_name', repos = 'https://cloud.r-project.org/')
}
```

**Why this pattern?**
- Checks if package is already loaded
- Downloads from CRAN if needed
- Non-blocking (uses `quietly = TRUE`)
- Idempotent (safe to run multiple times)
- Repo uses HTTPS (required for Alpine security)

---

## HTTP Requests Without `httr`

### Problem
Alpine filesystem doesn't include `httr` (HTTP client library). It's heavy and not essential.

### Solution
Use base R `readLines()` with `jsonlite` for JSON parsing:

```r
# Download JSON from API
url <- "https://jsonplaceholder.typicode.com/posts"
json_text <- readLines(url, warn = FALSE)
data <- jsonlite::fromJSON(paste(json_text, collapse = ''), flatten = TRUE)
```

**Why this works:**
- `readLines()` is built-in to R
- Works over HTTPS with Alpine's SSL
- `jsonlite::fromJSON()` handles parsing
- `paste()` joins lines for parsing

### Error Handling

```r
tryCatch({
  json_text <- readLines(url, warn = FALSE)
  data <- jsonlite::fromJSON(paste(json_text, collapse = ''), flatten = TRUE)
  cli::cli_alert_success('Loaded {nrow(data)} rows')
}, error = function(e) {
  cli::cli_alert_danger('Error: {e$message}')
})
```

---

## Modern R Piping

### Native Pipe Syntax (R 4.1+)

Alpine provides R 4.2+, so use native pipe `|>`:

```r
result <- data |>
  dplyr::mutate(new_col = value * 2) |>
  dplyr::filter(new_col > 10) |>
  dplyr::arrange(new_col)
```

**NOT** the magrittr pipe `%>%` (though it works):
```r
# Still works, but native pipe is preferred
result <- data %>%
  dplyr::mutate(...)
```

---

## Terminal Output with `cli`

### Headers and Sections

```r
cli::cli_h1('Main Title')        # Level 1 header
cli::cli_h2('Subtitle')          # Level 2 header
cli::cli_h3('Sub-subtitle')      # Level 3 header
```

### Status Messages

```r
cli::cli_alert_success('Operation completed')
cli::cli_alert_warning('Check this carefully')
cli::cli_alert_danger('This is an error!')
cli::cli_alert_info('FYI: {nrow(data)} rows')
```

### Visual Separators

```r
cli::cli_divider()         # Horizontal line
cli::cli_rule('Section')   # Decorative rule with text
```

### String Interpolation

```r
rows <- 1000
cols <- 50
cli::cli_text('Dataset: {rows} rows × {cols} columns')
```

**Note**: Use `{variable}` syntax, NOT `${variable}` or `${}` - those are R/TeX, not cli!

---

## Interactive Visualization Pattern

### Basic Plotly Scatter

```r
if (!requireNamespace('plotly', quietly = TRUE)) {
  install.packages('plotly', repos = 'https://cloud.r-project.org/')
}

chart <- plotly::plot_ly(
  data = mtcars,
  x = ~hp,
  y = ~mpg,
  type = 'scatter',
  mode = 'markers',
  marker = list(size = 8, opacity = 0.7)
) %>%
  plotly::layout(
    title = 'Chart Title',
    xaxis = list(title = 'X Axis'),
    yaxis = list(title = 'Y Axis'),
    hovermode = 'closest'
  )

htmlwidgets::saveWidget(chart, '/root/chart.html', selfcontained = FALSE)
```

**Key Parameters:**
- `selfcontained = FALSE` - Creates lighter HTML (resources separate)
- `hovermode = 'closest'` - Better for overlapping points
- `marker = list(opacity = 0.7)` - Transparency for density

### ggplot2 to Plotly

```r
p <- ggplot(data, aes(x = hp, y = mpg)) +
  geom_point(color = 'steelblue', size = 3) +
  theme_minimal()

interactive_chart <- plotly::ggplotly(p, tooltip = 'all') %>%
  plotly::layout(hovermode = 'closest')

htmlwidgets::saveWidget(interactive_chart, '/root/ggplotly.html', selfcontained = FALSE)
```

---

## Data Manipulation Patterns

### Tidyverse Pipe Workflow

```r
clean_data <- raw_data |>
  # Step 1: Parse/convert types
  dplyr::mutate(
    date = lubridate::ymd(date_str),
    category = stringr::str_to_lower(category)
  ) |>
  # Step 2: Filter
  dplyr::filter(!is.na(date), value > 0) |>
  # Step 3: Select relevant columns
  dplyr::select(id, date, category, value) |>
  # Step 4: Remove duplicates
  dplyr::distinct() |>
  # Step 5: Arrange
  dplyr::arrange(date, category)
```

### String Operations

```r
texts <- c(
  'The Quick Brown Fox',
  'jumps over the lazy dog',
  'HELLO WORLD'
)

summary <- data.frame(
  original = texts,
  lower = stringr::str_to_lower(texts),
  length = stringr::str_length(texts),
  word_count = stringr::str_count(texts, '\\b\\w+\\b'),
  has_upper = stringr::str_detect(texts, '[A-Z]')
)
```

### Date Processing

```r
dates <- data |>
  dplyr::mutate(
    date_parsed = lubridate::ymd(date_string),
    year = lubridate::year(date_parsed),
    month = lubridate::month(date_parsed, label = TRUE),
    week = lubridate::week(date_parsed),
    is_weekend = lubridate::wday(date_parsed) %in% c(1, 7)
  )
```

---

## Statistical Analysis

### Descriptive Statistics

```r
if (!requireNamespace('psych', quietly = TRUE)) {
  install.packages('psych', repos = 'https://cloud.r-project.org/')
}

stats <- psych::describe(
  Filter(is.numeric, mtcars),
  na.rm = TRUE,
  skew = FALSE,
  range = TRUE
)

print(stats)  # Includes: n, mean, sd, median, min, max, range
```

### Correlation Analysis

```r
numeric_cols <- Filter(is.numeric, mtcars)
corr_matrix <- cor(numeric_cols, use = 'pairwise.complete.obs')

# Test for significance
corr_test <- psych::corr.test(numeric_cols)
print(corr_test$r)         # Correlation coefficients
print(corr_test$p)         # P-values
```

### Correlation Heatmap

```r
png('/root/heatmap.png', width = 1000, height = 1000, res = 144)

corrplot::corrplot(
  corr_matrix,
  method = 'color',        # Color cells
  type = 'upper',          # Show upper triangle
  order = 'hclust',        # Hierarchical clustering
  tl.col = 'black',        # Text color
  tl.srt = 45,             # Text rotation
  col = viridis::viridis(200),  # Color palette
  addCoef.col = 'white',   # Show correlation values
  main = 'Correlation Analysis'
)

dev.off()
```

---

## High-Performance Operations with `data.table`

### Conversion and Basic Operations

```r
if (!requireNamespace('data.table', quietly = TRUE)) {
  install.packages('data.table', repos = 'https://cloud.r-project.org/')
}

# Convert to data.table
dt <- data.table::as.data.table(mtcars)

# Group-wise summary
summary <- dt[, .(
  mean_mpg = mean(mpg),
  max_hp = max(hp),
  n = .N
), by = cyl]

print(summary)
```

### Complex Aggregations

```r
# Multi-column grouping
result <- dt[
  ,
  .(
    avg_mpg = mean(mpg),
    total_wt = sum(wt),
    hp_range = max(hp) - min(hp),
    count = .N
  ),
  by = .(cyl, vs)
]

# Filtering with aggregation
result <- dt[hp > 100, .(mean_mpg = mean(mpg), count = .N), by = cyl]
```

### Why data.table?
- **Speed**: 10-50x faster than dplyr for 1M+ rows
- **Memory**: Lower memory footprint
- **Conciseness**: Powerful syntax for complex operations
- **Key operations**: Indexing, rolling joins, fast filtering

---

## Color Analysis with `farver`

### Multi-Space Conversion

```r
if (!requireNamespace('farver', quietly = TRUE)) {
  install.packages('farver', repos = 'https://cloud.r-project.org/')
}

colors <- c('red', 'green', 'blue', 'yellow')

# Convert from CSS names to different spaces
rgb_vals <- farver::decode_colour(colors, from = 'css', to = 'rgb')
lab_vals <- farver::decode_colour(colors, from = 'css', to = 'lab')
hcl_vals <- farver::decode_colour(colors, from = 'css', to = 'hcl')
```

### Perceptual Distance

```r
# All pairwise distances in Lab space
distances <- farver::compare_colour(
  lab_vals,           # Reference colors
  lab_vals,           # Compare to colors
  from_space = 'lab'  # Color space (perceptually uniform)
)

# Find most similar color to reference
ref_idx <- 1
similarities <- data.frame(
  color = colors,
  distance = distances[ref_idx, ]
)
```

### Practical Use Case: Color Accessibility

```r
# Check contrast ratio for accessibility
colors_to_check <- c('white', 'yellow', 'lime')
lab_colors <- farver::decode_colour(colors_to_check, from = 'css', to = 'lab')

# White is typically L = 100
white_idx <- which.max(lab_colors[, 1])

contrasts <- distances[white_idx, ]  # Distance from white
# Higher distance = better contrast on white background
```

---

## File I/O Operations

### CSV Import with Type Detection

```r
# readr automatically detects types
data <- readr::read_csv('/root/data.csv')

# See the detected schema
print(readr::spec(data))

# Control type detection
data <- readr::read_csv(
  '/root/data.csv',
  col_types = readr::cols(
    id = readr::col_integer(),
    name = readr::col_character(),
    date = readr::col_date('%Y-%m-%d')
  )
)
```

### JSON Export

```r
# Export with formatting
jsonlite::write_json(
  data,
  '/root/output.json',
  pretty = TRUE,      # Pretty-print
  na = 'null',        # NA becomes null
  force = TRUE        # Overwrite if exists
)

# Read back
data_from_json <- jsonlite::read_json('/root/output.json', simplifyVector = TRUE)
```

### Base R CSV (fallback)

```r
# If readr not available
data <- utils::read.csv(
  '/root/data.csv',
  stringsAsFactors = FALSE,
  na.strings = c('NA', 'N/A', '')
)
```

---

## Error Handling Strategy

### Defensive Programming

```r
# Check before operations
if (!('value' %in% names(data))) {
  cli::cli_alert_danger('Column "value" not found')
  stop('Missing required column')
}

# Validate data
if (nrow(data) == 0) {
  cli::cli_alert_warning('No rows in dataset')
}

# Safe filtering
numeric_cols <- Filter(is.numeric, data)
if (length(numeric_cols) == 0) {
  cli::cli_alert_danger('No numeric columns found')
} else {
  cli::cli_alert_success('{length(numeric_cols)} numeric columns')
}
```

### Try-Catch Pattern

```r
tryCatch({
  # Try this code
  result <- risky_operation(data)
  cli::cli_alert_success('Operation succeeded')
  result
}, error = function(e) {
  # If error occurs
  cli::cli_alert_danger('Error: {e$message}')
  NULL
}, warning = function(w) {
  # If warning occurs
  cli::cli_alert_warning('Warning: {w$message}')
})
```

---

## Performance Tips

1. **Vectorization**: Always work with vectors, avoid loops
   ```r
   # Good - vectorized
   squared <- mtcars$mpg ^ 2
   
   # Bad - loop
   squared <- c()
   for (val in mtcars$mpg) squared <- c(squared, val ^ 2)
   ```

2. **Use `data.table` for large datasets**
   ```r
   # For 1M+ rows
   dt <- data.table::as.data.table(huge_data)
   result <- dt[, mean(value), by = group]  # Fast!
   ```

3. **Pre-allocate vectors**
   ```r
   # Good - pre-allocate
   result <- numeric(1000)
   for (i in 1:1000) result[i] <- i ^ 2
   ```

4. **Use `stringr` for text operations**
   ```r
   # Good - vectorized
   lengths <- stringr::str_length(texts)
   
   # Bad - loop
   lengths <- c()
   for (txt in texts) lengths <- c(lengths, nchar(txt))
   ```

---

## Common Pitfalls & Solutions

| Problem | Cause | Solution |
|---------|-------|----------|
| `Error: object 'x' not found` | Variable name typo | Check namespace with `ls()` |
| `Error: could not find function` | Package not loaded | Use `library(pkg)` or `pkg::func()` |
| `readLines() failed` | Network/URL issue | Check HTTPS, use `warn=FALSE` |
| `NaN` in calculations | Division by zero | Use `na.rm = TRUE` parameter |
| Slow aggregations | Large dataset + dplyr | Switch to `data.table` |
| Memory error | Dataset too large for Alpine | Use `data.table`, process in chunks |
| Wrong dates | Format mismatch | Use `lubridate::parse_date_time()` |
| Color not recognized | Invalid CSS name | Use `farver::decode_colour(..., from='css')` |

---

## Output File Locations

All generated output goes to `/root/`:

```r
/root/plot.png                    # Static plots
/root/plot.pdf                    # PDF plots
/root/chart.html                  # Interactive Plotly
/root/data.json                   # JSON export
/root/heatmap.png                 # Correlation plots
/root/report.html                 # HTML widgets
```

**Note**: `/root/` is the persistent user directory in Alpine PRoot environment.

---

## Recommended Workflow

```
1. Load data
   ↓
2. Explore structure
   ↓
3. Clean/validate
   ↓
4. Transform
   ↓
5. Analyze/visualize
   ↓
6. Export results
```

**R Template**:
```r
# 1. Load
data <- readr::read_csv('/root/raw_data.csv')
cli::cli_alert_success('Loaded {nrow(data)} rows')

# 2. Explore
cli::cli_h2('Data Structure')
print(dplyr::glimpse(data))

# 3. Clean
clean <- data |> tidyr::drop_na() |> dplyr::distinct()
cli::cli_alert_success('After cleaning: {nrow(clean)} rows')

# 4. Transform
transformed <- clean |>
  dplyr::mutate(...) |>
  dplyr::filter(...) |>
  dplyr::arrange(...)

# 5. Analyze
stats <- psych::describe(dplyr::select(transformed, where(is.numeric)))
print(stats)

# 6. Export
jsonlite::write_json(transformed, '/root/results.json', pretty = TRUE)
cli::cli_rule('Analysis Complete')
```

---

## Resources

- **dplyr**: https://dplyr.tidyverse.org/
- **readr**: https://readr.tidyverse.org/
- **stringr**: https://stringr.tidyverse.org/
- **lubridate**: https://lubridate.tidyverse.org/
- **tidyr**: https://tidyr.tidyverse.org/
- **ggplot2**: https://ggplot2.tidyverse.org/
- **plotly**: https://plotly.com/r/
- **data.table**: https://rdatatable.gitlab.io/data.table/
- **cli**: https://cli.r-lib.org/
- **farver**: https://github.com/thomasp85/farver

---

Generated: 2026-07-27  
Alpine R Technical Implementation Guide for DroidRStudio

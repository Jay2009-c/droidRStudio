# droidR-Studio: Native R Development Environment for Android

[![Platform](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com/)
[![Language](https://img.shields.io/badge/Language-Kotlin-blue.svg)](https://kotlinlang.org/)
[![UI](https://img.shields.io/badge/UI-Jetpack%20Compose-orange.svg)](https://developer.android.com/jetpack/compose)
[![Backend](https://img.shields.io/badge/Backend-Alpine%20Linux-teal.svg)](https://alpinelinux.org/)

**droidR Studio** is a professional-grade, mobile-first R development environment that brings the full power of the R statistical computing language to Android devices. Unlike simple consoles or web-wrappers, droidR Studio runs a native Alpine Linux environment using PRoot, enabling high-performance, offline data science directly on your phone or tablet.

> [!IMPORTANT]
> All features are not fully tested and may not work as intended. Functionality is highly dependent on the specific packages and configuration within the embedded Alpine filesystem.

---

## 🚀 Key Features

### 1. Professional R Editor & Terminal
*   **Integrated Scripting:** A clean, modern editor for writing and saving R scripts.
*   **Live Output:** A dedicated terminal capturing standard output and error streams from the R environment.
*   **Persistent Storage:** Scripts and data are saved within the persistent Alpine `/root` directory.

### 2. Professional Plot Suite (Quick Plot)
Generate publication-quality visualizations via an intuitive GUI wizard:
*   **Engines:** Choose between **ggplot2** (Grammar of Graphics) and **Base R**.
*   **Plot Types:** Support for Histograms, Scatter Plots, Line Plots, and Boxplots.
*   **Interactivity:** Optional **Plotly** integration to create interactive, zoomable HTML charts.
*   **Aesthetics:** Deep customization of themes (Minimal, Classic, BW, Gray), color palettes (Viridis, Magma, Plasma, Cividis), transparency, and labels.

### 3. Data Science Coach (Data Tools)
A guided workflow system for common data science tasks using industry-standard R packages:
*   **Data Wrangling:** Clean missing values and filter duplicates using `dplyr` and `tidyr`.
*   **Statistical Analysis:** Deep numerical summaries via `psych`.
*   **Correlation Heatmaps:** Professional correlation matrices using `corrplot`.
*   **Smart CSV Import:** Fast reading with intelligent type detection via `readr`.
*   **Web Data:** Import JSON from public HTTPS APIs using `jsonlite`.
*   **Advanced Tools:** Time series analysis, string manipulation (`stringr`), and color space conversion (`farver`).

### 4. Native Linux Backend
*   **Alpine Linux Integration:** Uses a lightweight Alpine Linux rootfs for minimal footprint and maximum performance.
*   **PRoot Virtualization:** Executes Linux processes on Android without requiring root access.
*   **Package Management:** Pre-configured to work with CRAN, allowing for dynamic package installation.

### 5. Modern Android Experience
*   **Material 3 & Jetpack Compose:** A sleek, adaptive UI following the latest Android design principles.
*   **Edge-to-Edge Design:** Fully immersive interface utilizing the entire screen.
*   **OTA Updates:** Built-in system for seamless app updates.

---

## 🛠️ Technical Architecture

### Project Overview
This project bundles an isolated Alpine Linux root filesystem (rootfs) containing an R compiler toolchain inside a standalone, non-rooted Android application. It achieves this by embedding a Termux-compiled PRoot binary and its companion plugins directly as native app assets.

### Core Architectural Rules
1.  **Host vs. Guest Distinction**: The PRoot binary runs on the **Android Host layer** and MUST use Android's Bionic C library (`libc.so`) to run. Conversely, the R compiler runs inside the **Guest layer (Alpine)** and MUST use Alpine's Musl C library (`ld-musl-aarch64.so.1`). Mixing these up causes immediate Segmentation Faults.
2.  **The lib64 Path Fix**: Compiler tools inside Alpine often hardcode lookups for `/lib64/libc.so` or Termux-specific paths. droidR Studio resolves this either by creating a symbolic link directly within the Guest rootfs (`/lib64/libc.so -> /lib/ld-musl-aarch64.so.1`) or by using PRoot's bind (`-b`) argument at runtime to route those paths transparently to Alpine's internal Musl library file.

### The "Bridge" Mechanism (`AlpineRBridge`)
The core of the app is the `AlpineRBridge`, which orchestrates the PRoot environment:
*   **Rootfs Management:** Extracts and maintains a minimal Alpine Linux filesystem.
*   **Process Execution:** Uses `libproot.so` to "trick" the R runtime into thinking it's running in a standard Linux environment.
*   **Bindings:** Mounts `/dev`, `/proc`, and `/sys` to ensure the guest environment can interact with the system where necessary.
*   **Network Config:** Automatically configures DNS (8.8.8.8) to allow R packages to be downloaded from CRAN.

### Pre-configured R Stack
The environment comes ready with a powerful suite of packages:
*   **Visualization:** `ggplot2`, `plotly`, `corrplot`, `scales`, `viridis`.
*   **Tidyverse:** `dplyr`, `tidyr`, `readr`, `stringr`, `tibble`, `lubridate`.
*   **Performance:** `data.table`, `Rcpp`.
*   **Utility:** `jsonlite`, `cli`, `farver`, `psych`.

---

## 🛠️ JNI Libraries & Native Assets

To enable PRoot virtualization, the application requires a specific set of native binaries staged in the `jniLibs` directory.

### Required Directory Structure
Visual file tree showing the exact native assets configuration under the 64-bit ARM architecture folder:

```text
src/main/jniLibs/arm64-v8a/
├── libandroid-shmem.so   (System V shared memory emulation plugin)
├── libc.so               (64-bit Android Bionic system C library)
├── libproot.so           (The main Termux PRoot executable binary, renamed to .so for Gradle packaging)
├── libproot-loader.so    (PRoot background extension loader hook)
└── libtalloc.so.2        (Hierarchical memory allocator dependency)
```

> [!TIP]
> These libraries are also mapped to the `assets` source set via `build.gradle.kts` (`assets.directories.add("src/main/jniLibs/arm64-v8a")`), allowing the app to manually "install" versioned dependencies like `libtalloc.so.2` that AGP would otherwise exclude from the native library directory.

---

## Deep Dive: Step-by-Step Extraction & Storage Guide

Follow these steps to manually extract host binaries and customize the Alpine Guest environment using a Termux build environment.

### Step 1: Setup a Staging Area in the Shared Download Directory
Before copying, ensure Termux has storage permissions and create a dedicated folder inside the public Android Download directory. This makes the files easily accessible to Android Studio or file managers:

```bash
# Request storage permission
termux-setup-storage
# Create staging directory in public storage
mkdir -p /sdcard/Download/app-native-assets
```

### Step 2: Extracting and Exporting Host Binaries from Termux
Install the necessary tools in Termux and copy and rename the files directly from their system locations to the public shared Download staging directory:

```bash
# Update and install dependencies
pkg update && pkg upgrade -y
pkg install proot libandroid-shmem talloc

# Copy and rename binaries for Android packaging
cp /data/data/com.termux/files/usr/bin/proot /sdcard/Download/app-native-assets/libproot.so
cp /data/data/com.termux/files/usr/libexec/proot/libproot-loader.so /sdcard/Download/app-native-assets/libproot-loader.so
cp /data/data/com.termux/files/usr/lib/libandroid-shmem.so /sdcard/Download/app-native-assets/libandroid-shmem.so
cp /data/data/com.termux/files/usr/lib/libtalloc.so.2 /sdcard/Download/app-native-assets/libtalloc.so.2
```

### Step 3: Extracting and Exporting Host libc.so from Android
You must pull the 64-bit Bionic library directly from the device:

```bash
cp /system/lib64/libc.so /sdcard/Download/app-native-assets/libc.so
```
> [!WARNING]
> NEVER pull from `/system/lib/`. 32-bit layers will fail on modern 64-bit ARM architectures (`aarch64`).

### Step 4: Environment Customization (Termux Workflow)
This sequence handles repository patches and package initialization to compile the final rootfs payload.

```bash
# 1. Purge legacy build caches and pull target container frameworks
pkg install proot-distro -y
proot-distro remove alpine
proot-distro install alpine
proot-distro login alpine

# 2. Re-route DNS mappings to handle sandboxed web-hooks
echo "nameserver 8.8.8.8" > /etc/resolv.conf

# 3. Patch Alpine Mirror Array Configuration (Downgrade to HTTP to bypass PRoot TLS timeouts)
sed -i 's/https/http/g' /etc/apk/repositories
printf "http://alpinelinux.org\nhttp://alpinelinux.org\n" > /etc/apk/repositories
rm -rf /var/cache/apk/*

# 4. Inject runtime environments and full POSIX filesystem packaging utilities
apk update && apk add R R-dev tar gzip

# 5. Core Environment Verification Test
R --version
exit

# 6. Execute root extraction loop (Excluding virtual file systems)
termux-setup-storage
cd $PREFIX/var/lib/proot-distro/containers/alpine/
tar --exclude='rootfs/dev/*' --exclude='rootfs/proc/*' --exclude='rootfs/sys/*' -czf alpine_r.tar.gz rootfs/

# 7. Relocate compiled artifacts
cp alpine_r.tar.gz /storage/emulated/0/Download/
```

### Phase 5: Embedded App Asset Layout Structure
```text
app/src/main/assets/
  ├── alpine_r.tar.gz   <-- Pre-baked Alpine Linux Environment (Contains R/Rscript)
```

---

## ⚙️ App Runtime Integration

The application execution layer must run these assets using isolated paths and specific environment variables.

### Execution Strategy
1.  **Library Path**: Enforce setting `LD_LIBRARY_PATH` point to the app context's native library dir (`/data/data/your.package.name/lib/`) so the host system can execute `libproot.so`.
2.  **Path Configuration**: Set `PROOT_LOADER` to point to `libproot-loader.so` and `PROOT_TMP_DIR` to a writable cache directory.
3.  **Isolated Execution & Mapping**: Use the `--isolated` flag and the transparent mapping fix where necessary.

**Example PRoot Command:**
```bash
./libproot.so \
  --isolated \
  -r /path/to/alpine-rootfs \
  -b /path/to/alpine-rootfs/lib/ld-musl-aarch64.so.1:/data/data/com.termux/files/usr/lib64/libc.so \
  -0 \
  /usr/bin/R
```

**Live App Execution Context:**
The current `AlpineRBridge.kt` implementation leverages host-side environment variables and standard binds for `/dev`, `/proc`, and `/sys` to manage the bridge:

```bash
LD_LIBRARY_PATH=/data/data/pkg/files/native-libs:/data/data/pkg/lib \
PROOT_TMP_DIR=/data/data/pkg/cache/proot-tmp \
PROOT_LOADER=/data/data/pkg/lib/libproot-loader.so \
./libproot.so \
  -0 \
  -r /path/to/alpine/rootfs \
  -b /dev \
  -b /proc \
  -b /sys \
  -w /root \
  /usr/bin/Rscript \
  script.R
```

---

## 📦 Installation & Setup

1.  **Clone the Repository:**
    ```bash
    git clone https://github.com/Jay2009-c/droidRStudio.git
    ```
2.  **Open in Android Studio:** Open the project as a standard Android Gradle project.
3.  **Build & Run:** Deploy to an `arm64-v8a` device (required for the PRoot loader).
4.  **Initial Extraction:** Upon first launch, the app will extract the Alpine Linux rootfs from the assets. This may take a minute.
    > [!TIP]
    > To force a re-extraction during development, increment the `currentVersion` string in `MainActivity.kt`.

---

## 📈 Supported Workflows

### CSV Import/Export
The app supports importing CSV files from the Android file system into the R environment. Once processed, you can export your results or plots back to your device's storage.

### Interactive Visualizations
Using the `plotly` engine, you can generate HTML-based interactive charts that can be opened in any mobile browser, providing a desktop-like exploration experience on the go.

### API Integration
Fetch live data from the web directly into your R environment using the built-in API import tool, which leverages `jsonlite` and base R's networking capabilities.

---

## 🔧 Technical Requirements
*   **Architecture:** `arm64-v8a` (required for native PRoot virtualization).
*   **Android Version:** API 26 (Android 8.0) or higher.
*   **Storage:** Approximately 800MB for the Alpine environment and installed R packages.(But you can remove other unnecessary components in the filesystem before bundling in tarball with app for decreasing potential storage usage the app, I haven't removed other files/folders from the filesystem for preventing errors)

---

## 🤝 Contributing
Contributions are welcome! Whether it's adding new R tool templates, improving the editor, optimizing the PRoot bridge, or expanding the platform support feel free to open an issue or submit a pull request.

---

## 📄 License
*   The Android source code is licensed under GPL-3.0.
*   The Alpine Linux environment and R packages are subject to their respective licenses.

---

Created by **Jay** | Empowering mobile data science with droidR Studio.

> [!NOTE]
> The app's codebase was partially **vibe coded** — built with a mix of technical rigor and creative intuition.
>
> [!CAUTION]
> Many features are experimental, not fully tested, and may not work as intended. Stability depends on the integrity of the rootfs and the specific R packages installed in the guest environment.

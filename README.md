# droidR Studio: Native R Development Environment for Android

[![Platform](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com/)
[![Language](https://img.shields.io/badge/Language-Kotlin-blue.svg)](https://kotlinlang.org/)
[![UI](https://img.shields.io/badge/UI-Jetpack%20Compose-orange.svg)](https://developer.android.com/jetpack/compose)
[![Backend](https://img.shields.io/badge/Backend-Alpine%20Linux-teal.svg)](https://alpinelinux.org/)

**droidR Studio** is a professional-grade, mobile-first R development environment that brings the full power of the R statistical computing language to Android devices. Unlike simple consoles or web-wrappers, droidR Studio runs a native Alpine Linux environment using PRoot, enabling high-performance, offline data science directly on your phone or tablet.

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

### Core Architecture
droidR-Studio is a standalone Android IDE that executes a fully functional ARM64 R-Language statistical compute environment locally on device without requiring root access.
The application bypasses Android's custom Bionic libc runtime limitations by embedding an ultra-lightweight Alpine Linux filesystem (musl libc) directly inside the APK assets. At runtime, the application leverages an isolated PRoot User-Space Sandbox execution engine to intercept, modify, and translate Linux kernel system calls, tricking the native compiled R binaries into running natively inside the Android user space container.

### Core Architectural Rules
*   **Host vs. Guest Distinction**: The PRoot binary runs on the **Android Host layer** and MUST use Android's Bionic C library (`libc.so`) to run. Conversely, the R compiler runs inside the **Guest layer (Alpine)** and MUST use Alpine's Musl C library (`ld-musl-aarch64.so.1`). Mixing these up causes immediate Segmentation Faults.
*   **The lib64 Path Fix**: Compiler tools inside Alpine often hardcode lookups for `/lib64/libc.so`. droidR Studio resolves this by creating a symbolic link directly within the Guest rootfs (`/lib64/libc.so -> /lib/ld-musl-aarch64.so.1`), ensuring that all guest processes find the Musl loader at the expected path without requiring runtime binds.

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
```text
app/src/main/jniLibs/arm64-v8a/
├── libandroid-shmem.so   (System V shared memory emulation plugin)
├── libc.so               (64-bit Android Bionic system C library)
├── libproot.so           (The main Termux PRoot executable binary, renamed to .so for Gradle packaging)
├── libproot-loader.so    (PRoot background extension loader hook)
└── libtalloc.so.2        (Hierarchical memory allocator dependency)
```

> [!TIP]
> These libraries are also mapped to the `assets` source set via `build.gradle.kts` (`assets.directories.add("src/main/jniLibs/arm64-v8a")`), allowing the app to manually "install" versioned dependencies like `libtalloc.so.2` that AGP would otherwise exclude from the native library directory.

---

## 🏗️ Deep Dive: Manual Reproduction & Customization

Follow these steps to manually extract host binaries and customize the Alpine Guest environment.

### Step 1: Extracting Host Binaries from Termux
Install the necessary tools in Termux and copy them to a staging directory:

```bash
# Update and install dependencies
pkg update && pkg upgrade -y
pkg install proot libandroid-shmem talloc

# Create a staging directory
mkdir -p ./staging_jni

# Copy and rename binaries for Android packaging
cp /data/data/com.termux/files/usr/bin/proot ./staging_jni/libproot.so
cp /data/data/com.termux/files/usr/libexec/proot/libproot-loader.so ./staging_jni/libproot-loader.so
cp /data/data/com.termux/files/usr/lib/libandroid-shmem.so ./staging_jni/libandroid-shmem.so
cp /data/data/com.termux/files/usr/lib/libtalloc.so.2 ./staging_jni/libtalloc.so.2
```

### Step 2: Extracting Host libc.so from Android
You must pull the 64-bit Bionic library directly from a device or emulator:

```bash
cp /system/lib64/libc.so ./staging_jni/libc.so
```
> [!WARNING]
> NEVER pull from `/system/lib/`. 32-bit layers will fail on modern 64-bit ARM architectures (`aarch64`).

### Step 3: Preparing the Alpine Guest rootfs Pathing
Download the official [Alpine Linux AArch64 Mini Rootfs](https://alpinelinux.org/downloads/). Before packaging, you must handle symlinks to ensure compiler tools can find the Musl library:

```bash
# Inside your extracted Alpine rootfs directory
mkdir -p ./lib64
ln -s ../lib/ld-musl-aarch64.so.1 ./lib64/libc.so
```

### Phase 4: Environment Customization (Termux Workflow)
This sequence handles repository patches, package initialization, and mirror mapping to compile the generic rootfs payload.

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
1.  **Environment Setup**: Enforce `LD_LIBRARY_PATH` to include both the app's native library directory and a custom "native-libs" directory where versioned dependencies (like `libtalloc.so.2`) are extracted.
2.  **Path Configuration**: Set `PROOT_LOADER` to point to `libproot-loader.so` and `PROOT_TMP_DIR` to a writable cache directory.
3.  **Core Bindings**: Use the `-b` (bind) flag to mount essential host virtual filesystems (`/dev`, `/proc`, `/sys`) into the Guest environment.

**Example PRoot Command:**
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
Contributions are welcome! Whether it's adding new R tool templates, improving the editor, or optimizing the PRoot bridge, feel free to open an issue or submit a pull request.

---

## 📄 License
*   The Android source code is licensed under GPL 3.0.
*   The Alpine Linux environment and R packages are subject to their respective licenses.

---

Created by **Jay** | Empowering mobile data science with droidR Studio.

> [!NOTE]
> This project was partially **vibe coded** — built with a mix of technical rigor and creative intuition.
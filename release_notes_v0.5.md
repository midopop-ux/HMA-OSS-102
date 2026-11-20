# HMA-102 v0.5 — Modern libxposed (API 102) Edition

> A high-performance, open-source fork of Hide My Applist dedicated to the **Modern libxposed API 102** framework. Built for maximum stealth, rock-solid stability, and battery efficiency without Zygisk bloat.

---

### 🌟 Key Highlights

- **Modern libxposed (API 102) Native Backend:** Complete migration from legacy Xposed to the official Modern libxposed API 102 specification (`META-INF/xposed/java_init.list`, `scope.list`).
- **Advanced Anti-Detection Hooks:** Intercepts `UsageStatsManager` and system broadcast queues to mask hidden apps from recent history and USB/ADB state sniffers.
- **CPU & Battery Optimization:** Introduces an O(1) package name accessor cache in `system_server`, eliminating thousands of reflection lookups and exceptions per second on Android 10–12.
- **Interactive SAF Backup & Restore:** Complete overhaul of backup management using Android Storage Access Framework (SAF) with dead-config trimming.
- **Expanded Modern Presets:** Native support for APatch, KernelSU Next, YukiSU, Duck Detector, and Chunqiu security checkers.
- **Permanent Release Signing:** Fully verified and signed with our permanent key (`CN=MIDO`) across all automated builds.

---

### 🛠️ Detailed Changelog (v161 ➔ v0.5)

#### ⚡ Core Engine & Architecture
* **API 102 Migration:** Transitioned the core hooking engine to `io.github.libxposed.api:102.0.0`.
* **XposedCompat Abstraction:** Built an internal compatibility layer providing explicit, type-safe hooker contracts (`CompatBeforeHooker`, `CompatAfterHooker`) that are resilient to aggressive R8 compiler stripping.
* **ProGuard / R8 Hardening:** Tailored keep rules ensuring optimal DEX bytecode minification while strictly protecting API 102 contracts, Fragment constructors, and serialization models.
* **Event-Driven Bootstrap:** Cleanly hooks `ServiceManager.addService` to acquire system binders immediately upon registration without polling or sleep delays.

#### 🛡️ Anti-Detection & System Hooks
* **`UsageStatsHook` (New):** Filters `UsageStatsService$BinderService.queryUsageStats` and `queryEvents` to prevent banking and security apps from discovering sensitive apps via recent usage logs.
* **`BroadcastHook` (New):**
  - Monitors system broadcast queues (`BroadcastProcessQueue`, `BroadcastQueueImpl`).
  - Hides USB debugging/ADB state (`ACTION_USB_STATE` / `USB_FUNCTION_ADB`).
  - Preserves legacy broadcast completion when no receivers are resolved (`receivers.isNullOrEmpty()`), dropping Duck Detector broadcast callback mismatches to zero.
* **System Component Protection:** Hardened whitelists against accidental isolation of critical packages (`com.google.android.permissioncontroller`, `com.miui.securitycenter`).

#### 🚀 Performance & Memory
* **Cached Package Accessors:** Implemented `ConcurrentHashMap` caching for `PackageSetting` name resolution in `Utils4Xposed.kt`. Resolves reflection lookups once per class and avoids repeated `NoSuchMethodException` traps on Android 10–12.
* **Preset In-Memory Cache:** Cached preset evaluations in `PresetCache.kt` for lag-free app lists, instant search, and seamless template switching.

#### 📋 Manager UI & Storage
* **SAF Backup & Restore UI (`BackupRestoreFragment`):**
  - Interactive file-picker integration for import and export.
  - Granular category selection: export/import app configs, templates, and global settings independently.
  - **Dead Config Trimming:** Automatically purges remnants of uninstalled applications from config files.
  - **Restore Modes:** Select between non-destructive merging (`Append`) or clean replacement (`Overwrite`).
* **Fork Identity & Navigation:**
  - Modern edge-to-edge system insets support (`setEdge2EdgeFlags`).
  - Updated About screen reflecting the fork's mission and repository links.
  - Added Fork Developer profile (`midopop-ux`) with GitHub avatar integration.

---

### 📦 Verification & Signature Details

- **Package Name:** `org.midopop_ux.hma_102`
- **Minimum Android Version:** Android 10 (API 29)
- **Target Android Version:** Android 16 (API 36)
- **APK Signer:** `CN=MIDO`
- **SHA-256 Fingerprint:** `49:1F:AE:C7:06:82:73:19:4B:D6:02:17:64:70:B6:E5:0A:AB:B3:73:A3:38:54:BA:DD:B5:C8:F9:C9:DB:DC:7E`

---

### 📥 Download & Installation

1. Ensure **LSPosed** (or any modern libxposed API 102 compatible framework) is installed.
2. Download [HMA-102-v0.5.apk](https://github.com/midopop-ux/HMA-OSS-102/releases/download/v0.5/HMA-102-v0.5.apk).
3. Install the APK, activate the module in your Xposed manager, and enable **System Framework** in its scope.
4. Reboot your device to apply system hooks.

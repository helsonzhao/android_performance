# The Art of Android Performance Optimization — Companion Source Code

**Author:** Zijian Zhao  
**Technical Blog:** [https://blog.helson.uk/tags/androidperformance](https://blog.helson.uk/tags/androidperformance)

---

## Overview

This repository contains the official companion Android project for the book ***The Art of Android Performance Optimization*** by **Zijian Zhao**. It provides hands-on Java, Kotlin, C/C++ (JNI / NDK), and Gradle ASM bytecode instrumentation implementations corresponding to the practical engineering chapters of the book.

---

## Architecture & Module Mapping

### 1. Memory Optimization (Chapter 3)
* **Package:** `com.example.performance_optimize.memory`
* **Native Sources:** `app/src/main/cpp/hook_functions.cpp`, `app/src/main/cpp/mock_native_leak.cpp`, `app/src/main/cpp/optimize.cpp`
* **Key Implementations:**
  * **Section 3.1 — Java Memory Leak Detection:** `JavaLeakActivity.java` (Main-thread `Handler` delayed message leak reproduction & LeakCanary / HPROF verification).
  * **Section 3.2 — Native Memory Leak Detection:** `NativeLeakActivity.java` & `hook_functions.cpp` (Manual ELF `.got.plt` parsing and `ByteHook` interception of `malloc`, `_Unwind_Backtrace` CFI stack unwinding, and `dladdr` runtime symbolization).
  * **Section 3.3 — Bitmap Governance:** `BitmapGovernanceHelper.java` (`ViewTreeObserver.OnPreDrawListener` oversized `Bitmap` detector, two-pass `inJustDecodeBounds` + `inSampleSize` downsampling, and `WeakReference`/`ReferenceQueue` lifecycle tracking).
  * **Section 3.4 — Thread Stack Shrinking:** `optimize.cpp` (`shrinkThreadStack()` hooking `pthread_attr_setstacksize` in `libart.so` to halve default 1 MB thread stacks to 512 KB).
  * **Section 3.5 — Releasing Default WebView Reserved Memory:** `optimize.cpp` (`releaseWebViewReservation()` scanning `/proc/self/maps` and unmapping `[anon:libwebview reservation]` via `munmap()`).

### 2. Speed & Fluency Optimization (Chapter 5)
* **Package:** `com.example.performance_optimize.speed`
* **Native Sources:** `app/src/main/cpp/optimize.cpp`
* **Key Implementations:**
  * **Section 5.1 — Utilizing CPU Idle Windows:** `SpeedExampleActivity.java` & `optimize.cpp` (`getProcessCpuTicks()` using POSIX `times()` and `/proc/stat`).
  * **Section 5.2 — Reducing CPU Wait States:** Zero-copy `MappedByteBuffer` (`mmap`) file I/O in `SpeedExampleActivity.java`.
  * **Section 5.3 — Binding Critical Threads to Big CPU Cores:** `optimize.cpp` (`discoverBigCores()` reading `/sys/devices/system/cpu/cpuX/cpufreq/cpuinfo_max_freq` and binding threads via `sched_setaffinity()`).
  * **Section 5.4 — ART Garbage Collection (GC) Suppression:** `optimize.cpp` (`enableGcSuppressionWindow()` hooking `art::gc::HeapTaskDaemon::Run`).
  * **Section 5.5 — Cache Eviction Strategy Optimization:** `LFUCache.java` (Strict $O(1)$ Least Frequently Used cache backed by doubly-linked frequency buckets).
  * **Section 5.7 — Elevating Core Thread Priorities:** `SpeedExampleActivity.java` (`findRenderThreadTid()` via `/proc/self/task` and `Process.setThreadPriority(tid, -19)`).
  * **Section 5.8 — Unified Thread Pool Governance:** `MonitoredThreadPool.java` (Separated CPU-bound and I/O-bound bounded `ThreadPoolExecutor`s with slow-task watchdog instrumentation).

### 3. Stability Optimization (Chapter 7)
* **Package:** `com.example.performance_optimize.stability`
* **Native Sources:** `app/src/main/cpp/native_capture.cpp`, `app/src/main/cpp/mock_native_crash.cpp`, `app/src/main/cpp/hook_functions.cpp`
* **Key Implementations:**
  * **Section 7.1 — Native Crash Monitoring:** `native_capture.cpp` (Custom POSIX signal handler with 16 KB `sigaltstack` for `SIGSEGV`/`SIGABRT`/`SIGFPE`/`SIGILL`/`SIGBUS` and Google Breakpad minidump generation).
  * **Section 7.2 — ANR Monitoring:** `native_capture.cpp`, `hook_functions.cpp`, & `StabilityExampleActivity.java` (`SIGQUIT` signal interception, `tgkill` forwarding to `SignalCatcher`, `ActivityManager.getProcessesInErrorState()` verification, and `libc.so` `write()` hook to capture ANR stack traces).
  * **Section 7.3 — OOM Monitoring & On-Device HPROF Tailoring:** `HprofStripper.java` (Streaming HPROF binary parser that strips primitive array payloads prior to upload).
  * **Section 7.6 — Main-Thread Slow Function Monitoring:** `SlowMethodMonitor.java` & `buildSrc` (`AsmClassVisitorFactory` + ASM `AdviceAdapter` bytecode instrumentation).

### 4. Package Size Optimization (Chapter 9)
* **Package:** `com.example.performance_optimize.apksize`
* **Key Implementations:**
  * **Section 9.1.2 — Duplicate APK Entry Scanner:** `DynamicPluginManager.scanDuplicateApkEntries()` (ZIP CRC-32 duplicate detector).
  * **Section 9.6.2 — Dynamic Resource Pack Loading:** `DynamicPluginManager.loadExternalResources()` (`AssetManager.addAssetPath` dynamic skin/resource loading).
  * **Section 9.7.2 — Dynamic Class Loading:** `DynamicPluginManager.loadExternalClass()` (`DexClassLoader`).
  * **Section 9.8.2 — Dynamic Native `.so` Directory Injection:** `DynamicPluginManager.injectNativeLibraryDir()` (`DexPathList.nativeLibraryDirectories` injection).

### 5. Power, Network Traffic, Disk & Graceful Degradation (Chapter 10)
* **Package:** `com.example.performance_optimize.other`
* **Key Implementations:**
  * **Section 10.1 — Power Consumption Monitoring:** `OtherOptimizeActivity.java` (`com.android.internal.os.PowerProfile` hardware profile inspection).
  * **Section 10.2 — Network Traffic Telemetry:** `OtherOptimizeActivity.java` (`TrafficStats.getUidRxBytes` / `getUidTxBytes` per-UID accounting).
  * **Section 10.3 — Sandbox Disk Footprint Governance:** `OtherOptimizeActivity.java` (Recursive sandbox storage analyzer).
  * **Section 10.4 — Dynamic Graceful Degradation Framework:** `DevicePerfManager.java` (Multi-tier `EXCELLENT`/`NORMAL`/`DEGRADED`/`CRITICAL` telemetry evaluator and prioritized degradation task scheduler).

---

## License

Copyright © Zijian Zhao. All rights reserved.

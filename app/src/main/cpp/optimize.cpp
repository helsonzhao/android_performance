#include <jni.h>
#include <android/log.h>
#include <dirent.h>
#include <pthread.h>
#include <sched.h>
#include <sys/mman.h>
#include <sys/times.h>
#include <unistd.h>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>
#include <vector>
#include <algorithm>
#include "bytehook.h"

#define LOG_TAG "PerfOptimizeNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

// ============================================================================
// Chapter 3.4: Thread Stack Shrinking via pthread_attr_setstacksize Hook
// ============================================================================
namespace {
    constexpr size_t kDefaultStackSizeThreshold = 1024 * 1024; // ~1 MB default stack
    constexpr size_t kOptimizedStackSize = 512 * 1024;         // 512 KB target stack

    int pthread_attr_setstacksize_hook(pthread_attr_t *attr, size_t stack_size) {
        BYTEHOOK_STACK_SCOPE();
        size_t final_size = stack_size;
        if (stack_size >= kDefaultStackSizeThreshold && stack_size <= kDefaultStackSizeThreshold + 64 * 1024) {
            final_size = kOptimizedStackSize;
            LOGI("Shrinking pthread stack size from %zu bytes to %zu bytes", stack_size, final_size);
        }
        return BYTEHOOK_CALL_PREV(pthread_attr_setstacksize_hook, attr, final_size);
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_performance_1optimize_memory_MemoryExampleActivity_shrinkThreadStack(
        JNIEnv *env, jobject thiz) {
    bytehook_hook_single(
            "libart.so",
            nullptr,
            "pthread_attr_setstacksize",
            reinterpret_cast<void *>(pthread_attr_setstacksize_hook),
            nullptr,
            nullptr);
    LOGI("Installed pthread_attr_setstacksize hook on libart.so");
}

// ============================================================================
// Chapter 3.5: Releasing [anon:libwebview reservation] Virtual Address Space
// ============================================================================
extern "C"
JNIEXPORT jlong JNICALL
Java_com_example_performance_1optimize_memory_MemoryExampleActivity_releaseWebViewReservation(
        JNIEnv *env, jobject thiz) {
    FILE *fp = fopen("/proc/self/maps", "r");
    if (!fp) {
        return 0;
    }
    char line[1024];
    jlong releasedBytes = 0;
    while (fgets(line, sizeof(line), fp)) {
        if (strstr(line, "[anon:libwebview reservation]") != nullptr) {
            unsigned long long start = 0;
            unsigned long long end = 0;
            if (sscanf(line, "%llx-%llx", &start, &end) == 2 && end > start) {
                size_t length = static_cast<size_t>(end - start);
                if (munmap(reinterpret_cast<void *>(start), length) == 0) {
                    releasedBytes += static_cast<jlong>(length);
                    LOGI("Unmapped [anon:libwebview reservation]: 0x%llx - 0x%llx (%zu bytes)",
                         start, end, length);
                }
            }
        }
    }
    fclose(fp);
    return releasedBytes;
}

// ============================================================================
// Chapter 5.1.2: Process CPU Time Measurement via POSIX times()
// ============================================================================
extern "C"
JNIEXPORT jlongArray JNICALL
Java_com_example_performance_1optimize_speed_SpeedExampleActivity_getProcessCpuTicks(
        JNIEnv *env, jobject thiz) {
    struct tms time_sample{};
    clock_t wall_ticks = times(&time_sample);
    jlong values[3];
    values[0] = static_cast<jlong>(wall_ticks);
    values[1] = static_cast<jlong>(time_sample.tms_utime + time_sample.tms_stime);
    values[2] = static_cast<jlong>(sysconf(_SC_CLK_TCK));

    jlongArray result = env->NewLongArray(3);
    env->SetLongArrayRegion(result, 0, 3, values);
    return result;
}

// ============================================================================
// Chapter 5.3: Discovering Big CPU Cores & Binding Thread Affinity
// ============================================================================
int getNumberOfCPUCores() {
    int cores = 0;
    DIR *dir = opendir("/sys/devices/system/cpu/");
    if (dir != nullptr) {
        struct dirent *ent;
        while ((ent = readdir(dir)) != nullptr) {
            std::string name = ent->d_name;
            if (name.rfind("cpu", 0) == 0 && name.length() > 3) {
                bool isCore = true;
                for (size_t i = 3; i < name.length(); i++) {
                    if (name[i] < '0' || name[i] > '9') {
                        isCore = false;
                        break;
                    }
                }
                if (isCore) {
                    cores++;
                }
            }
        }
        closedir(dir);
    }
    return cores > 0 ? cores : 1;
}

std::vector<int> discoverBigCores() {
    int numCores = getNumberOfCPUCores();
    std::vector<std::pair<int, int>> coreFreqs; // (freq, coreIndex)
    int maxFreq = -1;

    for (int i = 0; i < numCores; i++) {
        std::string filename = "/sys/devices/system/cpu/cpu" +
                               std::to_string(i) + "/cpufreq/cpuinfo_max_freq";
        FILE *fp = fopen(filename.c_str(), "r");
        if (fp != nullptr) {
            char line[128];
            if (fgets(line, sizeof(line), fp) != nullptr) {
                int freq = std::atoi(line);
                if (freq > 0) {
                    coreFreqs.emplace_back(freq, i);
                    if (freq > maxFreq) {
                        maxFreq = freq;
                    }
                }
            }
            fclose(fp);
        }
    }

    std::vector<int> bigCores;
    for (const auto &entry : coreFreqs) {
        // Select cores running at >= 75% of the peak cluster frequency
        if (entry.first >= (maxFreq * 3) / 4) {
            bigCores.push_back(entry.second);
        }
    }
    if (bigCores.empty()) {
        bigCores.push_back(numCores - 1);
    }
    return bigCores;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_example_performance_1optimize_speed_SpeedExampleActivity_bindCurrentThreadToBigCores(
        JNIEnv *env, jobject thiz) {
    std::vector<int> bigCores = discoverBigCores();
    cpu_set_t mask;
    CPU_ZERO(&mask);
    for (int coreId : bigCores) {
        CPU_SET(coreId, &mask);
    }
    pid_t tid = gettid();
    int rc = sched_setaffinity(tid, sizeof(mask), &mask);
    LOGI("Bound thread tid=%d to %zu big CPU core(s), rc=%d", tid, bigCores.size(), rc);
    return rc == 0 ? JNI_TRUE : JNI_FALSE;
}

// ============================================================================
// Chapter 5.4: Suppressing ART Garbage Collection during Latency-Critical Windows
// ============================================================================
namespace {
    volatile bool gSuppressGcActive = false;

    void HeapTaskDaemon_Run_Hook(void *thread_self) {
        BYTEHOOK_STACK_SCOPE();
        if (gSuppressGcActive) {
            LOGI("GC suppression window active: delaying HeapTaskDaemon by 3000 ms");
            usleep(3000 * 1000);
            gSuppressGcActive = false;
        }
        BYTEHOOK_CALL_PREV(HeapTaskDaemon_Run_Hook, thread_self);
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_performance_1optimize_speed_SpeedExampleActivity_enableGcSuppressionWindow(
        JNIEnv *env, jobject thiz) {
    gSuppressGcActive = true;
    // Hook the mangled C++ symbol for art::gc::HeapTaskDaemon::Run(art::Thread*)
    bytehook_hook_single(
            "libart.so",
            nullptr,
            "_ZN3art2gc14HeapTaskDaemon3RunEPNS_6ThreadE",
            reinterpret_cast<void *>(HeapTaskDaemon_Run_Hook),
            nullptr,
            nullptr);
    LOGI("GC suppression window armed for 3000 ms");
}

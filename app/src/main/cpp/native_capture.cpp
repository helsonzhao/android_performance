#include <jni.h>
#include <android/log.h>
#include <dlfcn.h>
#include <signal.h>
#include <unistd.h>
#include <pthread.h>
#include <cstdlib>
#include <cstring>
#include "breakpad/src/client/linux/handler/exception_handler.h"
#include "breakpad/src/client/linux/handler/minidump_descriptor.h"

#define LOG_TAG "NativeCrashAndAnr"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// ============================================================================
// Chapter 7.1.1: Custom POSIX Crash Signal Handler with Alternate Signal Stack
// ============================================================================
namespace {
    constexpr int kCrashSignals[] = {SIGSEGV, SIGABRT, SIGFPE, SIGILL, SIGBUS};
    constexpr size_t kNumCrashSignals = sizeof(kCrashSignals) / sizeof(kCrashSignals[0]);
    struct sigaction gOldCrashHandlers[32];

    void customCrashSignalHandler(int sig, siginfo_t *info, void *ucontext) {
        LOGE("Caught fatal native signal %d (si_code=%d, fault_addr=%p)",
             sig, info ? info->si_code : 0, info ? info->si_addr : nullptr);
        // Chain to the previously registered signal handler (Breakpad / debuggerd)
        if (sig >= 0 && sig < 32 && gOldCrashHandlers[sig].sa_sigaction != nullptr) {
            gOldCrashHandlers[sig].sa_sigaction(sig, info, ucontext);
        } else {
            signal(sig, SIG_DFL);
            raise(sig);
        }
    }

    // Chapter 7.2.1: SIGQUIT (Signal 3) ANR Interception & Forwarding to SignalCatcher
    int gSignalCatcherTid = -1;

    int findSignalCatcherTid() {
        // Discover the ART SignalCatcher thread TID from /proc/self/task
        char path[128];
        FILE *fp = nullptr;
        for (int offset = 1; offset < 50; ++offset) {
            int candidateTid = getpid() + offset;
            snprintf(path, sizeof(path), "/proc/%d/task/%d/comm", getpid(), candidateTid);
            fp = fopen(path, "r");
            if (fp != nullptr) {
                char comm[64] = {0};
                if (fgets(comm, sizeof(comm), fp) != nullptr) {
                    if (strstr(comm, "Signal Catcher") != nullptr) {
                        fclose(fp);
                        return candidateTid;
                    }
                }
                fclose(fp);
            }
        }
        return -1;
    }

    void sigquitAnrHandler(int sig, siginfo_t *info, void *ucontext) {
        LOGE("Intercepted SIGQUIT (signal %d) — Potential ANR detected!", sig);
        int catcherTid = gSignalCatcherTid > 0 ? gSignalCatcherTid : findSignalCatcherTid();
        if (catcherTid > 0) {
            // Re-forward SIGQUIT to ART's SignalCatcher thread so system ANR dump completes
            syscall(__NR_tgkill, getpid(), catcherTid, SIGQUIT);
        }
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_performance_1optimize_stability_StabilityExampleActivity_installCustomSignalHandlers(
        JNIEnv *env, jobject thiz) {
    // 1. Allocate and register a 16 KB alternate signal stack (sigaltstack) for stack-overflow safety
    stack_t ss{};
    ss.ss_sp = malloc(16 * 1024);
    ss.ss_size = 16 * 1024;
    ss.ss_flags = 0;
    if (ss.ss_sp != nullptr) {
        sigaltstack(&ss, nullptr);
    }

    // 2. Register custom crash signal handlers with SA_ONSTACK | SA_SIGINFO
    struct sigaction sa{};
    memset(&sa, 0, sizeof(sa));
    sigemptyset(&sa.sa_mask);
    sa.sa_sigaction = customCrashSignalHandler;
    sa.sa_flags = SA_ONSTACK | SA_SIGINFO;
    for (size_t i = 0; i < kNumCrashSignals; ++i) {
        int sig = kCrashSignals[i];
        sigaction(sig, &sa, &gOldCrashHandlers[sig]);
    }

    // 3. Unblock SIGQUIT on the main thread to intercept ANR signals (Chapter 7.2.1)
    gSignalCatcherTid = findSignalCatcherTid();
    sigset_t sigquitMask;
    sigemptyset(&sigquitMask);
    sigaddset(&sigquitMask, SIGQUIT);
    pthread_sigmask(SIG_UNBLOCK, &sigquitMask, nullptr);

    struct sigaction anrSa{};
    memset(&anrSa, 0, sizeof(anrSa));
    sigemptyset(&anrSa.sa_mask);
    anrSa.sa_sigaction = sigquitAnrHandler;
    anrSa.sa_flags = SA_ONSTACK | SA_SIGINFO;
    sigaction(SIGQUIT, &anrSa, nullptr);
    LOGI("Installed custom crash signal handlers (with sigaltstack) and SIGQUIT ANR interceptor");
}

// ============================================================================
// Chapter 7.1.3: Google Breakpad Minidump Crash Capture
// ============================================================================
bool dumpCallback(const google_breakpad::MinidumpDescriptor &descriptor,
                  void *context,
                  bool succeeded) {
    LOGI("Wrote Breakpad minidump at %s (succeeded=%d)", descriptor.path(), succeeded);
    return false;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_performance_1optimize_stability_StabilityExampleActivity_captureNativeCrash(
        JNIEnv *env, jobject thiz) {
    google_breakpad::MinidumpDescriptor descriptor("/data/data/com.example.performance_optimize/");
    google_breakpad::ExceptionHandler eh(descriptor, nullptr, dumpCallback,
                                         nullptr, true, -1);
    int *ptr = nullptr;
    *ptr = 10;
}

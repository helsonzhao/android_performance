#include <jni.h>
#include <string>
#include <dlfcn.h>
#include <vector>
#include "bytehook.h"
#include <android/log.h>
#include <unistd.h>
#include <unwind.h>
#include <inttypes.h>
#include <linux/elf.h>
#include <sys/mman.h>
#include <iostream>
#include <fstream>
#include <cstring>

namespace {
    struct BacktraceState {
        void **current;
        void **end;
    };

    static _Unwind_Reason_Code unwindCallback(struct _Unwind_Context *context, void *arg) {
        BacktraceState *state = static_cast<BacktraceState *>(arg);
        uintptr_t pc = _Unwind_GetIP(context);
        if (pc) {
            if (state->current == state->end) {
                return _URC_END_OF_STACK;
            } else {
                *state->current++ = reinterpret_cast<void *>(pc);
            }
        }
        return _URC_NO_REASON;
    }
}

// Frame-pointer (FP) based stack unwinder (Chapter 3.2.2 & Chapter 7.1.2)
static void saveStackByFp(void *secret) {
    ucontext_t *uc = static_cast<ucontext_t *>(secret);
    int i = 0;
    Dl_info dl_info;
#if defined(__aarch64__)
    const void *const *frame_pointer = reinterpret_cast<const void *const *>(uc->uc_mcontext.regs[29]);
    const void *return_address = reinterpret_cast<const void *>(uc->uc_mcontext.pc);
#elif defined(__arm__)
    const void *const *frame_pointer = reinterpret_cast<const void *const *>(uc->uc_mcontext.arm_fp);
    const void *return_address = reinterpret_cast<const void *>(uc->uc_mcontext.arm_pc);
#else
    const void *const *frame_pointer = nullptr;
    const void *return_address = nullptr;
#endif
    printf("\nStack trace:");
    while (return_address) {
        memset(&dl_info, 0, sizeof(Dl_info));
        if (!dladdr(const_cast<void *>(return_address), &dl_info)) {
            break;
        }
        const char *sname = dl_info.dli_sname;
        // Print frame index, PC, symbol name, offset, and shared library path
        printf("%02d: %p <%s + %u> (%s)", ++i, return_address, sname,
               static_cast<unsigned int>(reinterpret_cast<uintptr_t>(return_address) -
                                         reinterpret_cast<uintptr_t>(dl_info.dli_saddr)),
               dl_info.dli_fname);
        if (!frame_pointer) {
            break;
        }
#if defined(__aarch64__)
        // On AArch64, FP (x29) points to {prev_fp, lr}
        return_address = frame_pointer[1];
        frame_pointer = reinterpret_cast<const void *const *>(frame_pointer[0]);
#elif defined(__arm__)
        // On ARM32, FP points to saved LR at [-1] and previous FP at [-2]
        return_address = frame_pointer[-1];
        frame_pointer = reinterpret_cast<const void *const *>(frame_pointer[-2]);
#else
        break;
#endif
    }
    printf("Stack trace end.");
}

void dumpBacktrace(void **buffer, size_t depth) {
    for (size_t idx = 0; idx < depth; ++idx) {
        void *addr = buffer[idx];
        Dl_info info;
        if (dladdr(addr, &info)) {
            const uintptr_t addr_relative =
                    reinterpret_cast<uintptr_t>(addr) - reinterpret_cast<uintptr_t>(info.dli_fbase);
            __android_log_print(ANDROID_LOG_DEBUG, "MallocHook",
                                "# %zu : %p : %s(%p)(%s)(%p)",
                                idx,
                                addr, info.dli_fname,
                                reinterpret_cast<void *>(addr_relative),
                                info.dli_sname ? info.dli_sname : "unknown",
                                info.dli_saddr);
        }
    }
}

void printNativeStack() {
    const size_t maxDepth = 30;
    void *buffer[maxDepth];
    BacktraceState state = {buffer, buffer + maxDepth};
    // Capture native call stack via libunwind
    _Unwind_Backtrace(unwindCallback, &state);
    size_t depth = state.current - buffer;
    // Symbolize and log stack frames
    dumpBacktrace(buffer, depth);
}

void *malloc_hook(size_t len) {
    BYTEHOOK_STACK_SCOPE();
    if (len > 1024 * 1024) {
        __android_log_print(ANDROID_LOG_DEBUG, "MallocHook", "Large allocation size: %zu bytes", len);
        printNativeStack();
    }
    return BYTEHOOK_CALL_PREV(malloc_hook, len);
}

ssize_t my_write(int fd, const void *const buf, size_t count) {
    BYTEHOOK_STACK_SCOPE();
    if (buf != nullptr && count > 0) {
        const char *content = static_cast<const char *>(buf);
        // Capture ANR trace dump output written by SignalCatcher thread (Chapter 7.2.3)
        if (strstr(content, "Cmd line:") != nullptr || strstr(content, "DALVIK THREADS") != nullptr) {
            std::ofstream file("/data/data/com.example.performance_optimize/example_anr.txt", std::ios::app);
            if (file.is_open()) {
                file.write(content, static_cast<std::streamsize>(count));
                file.close();
            }
        }
    }
    return BYTEHOOK_CALL_PREV(my_write, fd, buf, count);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_performance_1optimize_memory_NativeLeakActivity_hookMallocByBHook(
        JNIEnv *env,
        jobject thiz) {
    bytehook_hook_single(
            "libexample.so",
            nullptr,
            "malloc",
            reinterpret_cast<void *>(malloc_hook),
            nullptr,
            nullptr);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_performance_1optimize_stability_StabilityExampleActivity_hookAnrByBHook(
        JNIEnv *env,
        jobject thiz) {
    bytehook_hook_all(
            "libc.so",
            "write",
            reinterpret_cast<void *>(my_write),
            nullptr,
            nullptr);
}

// ============================================================================
// Chapter 3.2.1: Manual ELF PLT/GOT Hooking Reference Implementation
// ============================================================================
uintptr_t originFunc = 0;

void *malloc_hook_by_plt(size_t len) {
    __android_log_print(ANDROID_LOG_DEBUG, "hookMallocByPLTHook", "Intercepted malloc size: %zu", len);
    if (len > 20 * 1024 * 1024) {
        printNativeStack();
    }
    return reinterpret_cast<void *(*)(size_t)>(reinterpret_cast<void *>(originFunc))(len);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_performance_1optimize_memory_NativeLeakActivity_hookMallocByPLTHook(
        JNIEnv *env, jobject thiz) {
    FILE *fp = fopen("/proc/self/maps", "r");
    if (!fp) return;
    char line[1024];
    uintptr_t base_addr = 0;
    while (fgets(line, sizeof(line), fp)) {
        if (strstr(line, "libexample.so") != nullptr) {
            std::string targetLine = line;
            std::size_t pos = targetLine.find('-');
            if (pos != std::string::npos) {
                std::string addressStr = targetLine.substr(0, pos);
                base_addr = std::stoull(addressStr, nullptr, 16);
                break;
            }
        }
    }
    fclose(fp);
    if (base_addr == 0) return;

    // Parse ELF Header and Program Header Table to locate PT_DYNAMIC
    Elf32_Ehdr *header = reinterpret_cast<Elf32_Ehdr *>(base_addr);
    size_t phr_count = header->e_phnum;
    Elf32_Phdr *phdr_table = reinterpret_cast<Elf32_Phdr *>(base_addr + header->e_phoff);
    unsigned long dynamicAddr = 0;
    unsigned int dynamicSize = 0;
    for (size_t i = 0; i < phr_count; i++) {
        if (phdr_table[i].p_type == PT_DYNAMIC) {
            dynamicAddr = phdr_table[i].p_vaddr + base_addr;
            dynamicSize = phdr_table[i].p_memsz;
            break;
        }
    }
    if (dynamicAddr == 0) return;

    uintptr_t symbolTableAddr = 0;
    Elf32_Dyn *dynamic_table = reinterpret_cast<Elf32_Dyn *>(dynamicAddr);
    size_t dynCount = dynamicSize / sizeof(Elf32_Dyn);
    for (size_t i = 0; i < dynCount; i++) {
        if (dynamic_table[i].d_tag == DT_PLTGOT) {
            symbolTableAddr = dynamic_table[i].d_un.d_ptr + base_addr;
            break;
        }
    }
    if (symbolTableAddr == 0) return;

    // Make GOT memory page writable before patching
    mprotect(reinterpret_cast<void *>(symbolTableAddr & ~(PAGE_SIZE - 1)),
             PAGE_SIZE, PROT_READ | PROT_WRITE);
}

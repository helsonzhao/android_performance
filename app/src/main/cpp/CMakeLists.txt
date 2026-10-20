# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

# Sets the minimum version of CMake required to build the native library.

cmake_minimum_required(VERSION 3.10.2)
project("performance_optimize")

add_compile_options(-fno-omit-frame-pointer)

# Declares and names the project.
add_library(
        optimize
        SHARED
        optimize.cpp
        hook_functions.cpp)

add_library(
        example
        SHARED
        mock_native_leak.cpp
        mock_native_crash.cpp
        native_capture.cpp)
find_library( # Sets the name of the path variable.
        log-lib
        log)
find_package(bytehook REQUIRED CONFIG)


#breakpad
add_library(breakpad STATIC
        breakpad/src/client/linux/crash_generation/crash_generation_client.cc
        breakpad/src/client/linux/dump_writer_common/thread_info.cc
        breakpad/src/client/linux/dump_writer_common/ucontext_reader.cc
        breakpad/src/client/linux/handler/exception_handler.cc
        breakpad/src/client/linux/handler/minidump_descriptor.cc
        breakpad/src/client/linux/log/log.cc
        breakpad/src/client/linux/microdump_writer/microdump_writer.cc
        breakpad/src/client/linux/minidump_writer/linux_dumper.cc
        breakpad/src/client/linux/minidump_writer/linux_ptrace_dumper.cc
        breakpad/src/client/linux/minidump_writer/minidump_writer.cc
        breakpad/src/client/linux/minidump_writer/pe_file.cc
        breakpad/src/client/minidump_file_writer.cc
        breakpad/src/common/convert_UTF.cc
        breakpad/src/common/md5.cc
        breakpad/src/common/string_conversion.cc
        breakpad/src/common/linux/breakpad_getcontext.S
        breakpad/src/common/linux/elfutils.cc
        breakpad/src/common/linux/file_id.cc
        breakpad/src/common/linux/guid_creator.cc
        breakpad/src/common/linux/linux_libc_support.cc
        breakpad/src/common/linux/memory_mapped_file.cc
        breakpad/src/common/linux/safe_readlink.cc)
set_target_properties(breakpad PROPERTIES ANDROID_ARM_MODE arm)
set_property(SOURCE breakpad/src/common/linux/breakpad_getcontext.S PROPERTY LANGUAGE C)
target_include_directories(breakpad PUBLIC
        breakpad/src/common/android/include
        breakpad/src)

target_link_libraries(
        example
        ${log-lib}
        breakpad)

target_link_libraries(
        optimize
        ${log-lib}
        bytehook::bytehook)

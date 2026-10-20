# Toolchain used by Gradle: chains vcpkg to the NDK toolchain, picking the
# vcpkg triplet that matches the ABI Gradle is building for.

# try_compile() projects load this toolchain again without our cache
# variables; pass VCPKG_ROOT on through the environment.
if(NOT VCPKG_ROOT)
    set(VCPKG_ROOT "$ENV{VCPKG_ROOT}")
endif()
if(NOT VCPKG_ROOT)
    message(FATAL_ERROR "VCPKG_ROOT is not set; see os/android/README.md")
endif()
set(ENV{VCPKG_ROOT} "${VCPKG_ROOT}")

if(NOT ANDROID_NDK)
    if(CMAKE_ANDROID_NDK)
        set(ANDROID_NDK "${CMAKE_ANDROID_NDK}")
    else()
        set(ANDROID_NDK "$ENV{ANDROID_NDK_HOME}")
    endif()
endif()
if(NOT ANDROID_NDK)
    message(FATAL_ERROR "ANDROID_NDK is not set; build through Gradle, see os/android/README.md")
endif()
# vcpkg builds its ports outside of this CMake run; it finds the NDK through
# this environment variable.
set(ENV{ANDROID_NDK_HOME} "${ANDROID_NDK}")

if(NOT VCPKG_TARGET_TRIPLET AND NOT ANDROID_ABI)
    # Inside try_compile(); reuse the triplet of the main project.
    set(VCPKG_TARGET_TRIPLET "$ENV{OPENTTD_VCPKG_TRIPLET}")
endif()
if(NOT VCPKG_TARGET_TRIPLET)
    if(ANDROID_ABI STREQUAL "arm64-v8a")
        set(VCPKG_TARGET_TRIPLET "arm64-android")
    elseif(ANDROID_ABI STREQUAL "armeabi-v7a")
        set(VCPKG_TARGET_TRIPLET "arm-neon-android")
    elseif(ANDROID_ABI STREQUAL "x86_64")
        set(VCPKG_TARGET_TRIPLET "x64-android")
    elseif(ANDROID_ABI STREQUAL "x86")
        set(VCPKG_TARGET_TRIPLET "x86-android")
    else()
        message(FATAL_ERROR "Unsupported Android ABI: ${ANDROID_ABI}")
    endif()
endif()

set(ENV{OPENTTD_VCPKG_TRIPLET} "${VCPKG_TARGET_TRIPLET}")

set(VCPKG_CHAINLOAD_TOOLCHAIN_FILE "${ANDROID_NDK}/build/cmake/android.toolchain.cmake")
include("${VCPKG_ROOT}/scripts/buildsystems/vcpkg.cmake")

# Copyright 2025 Tanmay Patil
#
# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at http://mozilla.org/MPL/2.0/.
#
#

cmake_minimum_required(VERSION 3.22)

project(mlc_llm_android)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Find JNI - Not needed for Android NDK builds as jni.h is in sysroot
# find_package(JNI REQUIRED)

# Find Android logging library
find_library(log-lib log)

# ============================================================
# MLC-LLM Integration
# ============================================================
# 
# To enable full MLC-LLM functionality:
#
# 1. Clone MLC-LLM repository:
#    git clone --recursive https://github.com/mlc-ai/mlc-llm.git
#
# 2. Build for Android:
#    cd mlc-llm/android
#    ./scripts/build.sh
#
# 3. Copy built libraries to app/src/main/jniLibs/<ABI>/
#    - libtvm_runtime.so
#    - libmlc_llm.so
#
# 4. Set MLC_LLM_HOME to point to MLC-LLM installation
#    set(MLC_LLM_HOME "/path/to/mlc-llm")
#
# 5. Uncomment the following lines:
#
# set(MLC_LLM_HOME "$ENV{MLC_LLM_HOME}")
# if(EXISTS "${MLC_LLM_HOME}")
#     include_directories(${MLC_LLM_HOME}/cpp)
#     include_directories(${MLC_LLM_HOME}/3rdparty/tvm/include)
#     include_directories(${MLC_LLM_HOME}/3rdparty/tvm/3rdparty/dlpack/include)
#     include_directories(${MLC_LLM_HOME}/3rdparty/tvm/3rdparty/dmlc-core/include)
#     
#     # Link against MLC-LLM libraries
#     add_library(tvm_runtime SHARED IMPORTED)
#     set_target_properties(tvm_runtime PROPERTIES
#         IMPORTED_LOCATION ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libtvm_runtime.so)
#     
#     add_library(mlc_llm SHARED IMPORTED)
#     set_target_properties(mlc_llm PROPERTIES
#         IMPORTED_LOCATION ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libmlc_llm.so)
#     
#     set(MLC_LLM_LIBS tvm_runtime mlc_llm)
# endif()
# ============================================================

# JNI bridge library
add_library(mlc_llm_jni SHARED
    mlc_llm_jni.cpp
)

target_include_directories(mlc_llm_jni PRIVATE
    ${JNI_INCLUDE_DIRS}
)

target_link_libraries(mlc_llm_jni
    ${log-lib}
    # ${MLC_LLM_LIBS}  # Uncomment when MLC-LLM is integrated
)

# Enable optimizations for release builds
if(CMAKE_BUILD_TYPE STREQUAL "Release")
    target_compile_options(mlc_llm_jni PRIVATE
        -O3
        -ffast-math
        -DNDEBUG
    )
endif()

# ARM specific optimizations
if(ANDROID_ABI STREQUAL "arm64-v8a")
    target_compile_options(mlc_llm_jni PRIVATE
        -march=armv8-a+fp+simd
    )
endif()

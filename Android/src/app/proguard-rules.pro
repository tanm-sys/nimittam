# ProGuard rules for Nimittam (MLC-LLM Android App)
# Copyright 2025 Tanmay Patil - MPL-2.0

# ============================================================================
# TVM Runtime (CRITICAL - JNI bindings)
# ============================================================================
# TVM uses JNI native methods that are called from C++ code.
# R8 cannot see these usages and will strip the classes without these rules.

-keep class org.apache.tvm.** { *; }
-keepclassmembers class org.apache.tvm.** { *; }

# Keep all native method implementations
-keepclasseswithmembernames class org.apache.tvm.** {
    native <methods>;
}

# ============================================================================
# MLC-LLM Runtime (CRITICAL - JNI bindings)
# ============================================================================
# MLC-LLM Kotlin/Java classes that interface with native code

-keep class ai.mlc.mlcllm.** { *; }
-keepclassmembers class ai.mlc.mlcllm.** { *; }

# ============================================================================
# General JNI Protection
# ============================================================================
# Keep all classes with native methods across the app

-keepclasseswithmembernames class * {
    native <methods>;
}

# ============================================================================
# Kotlin Serialization
# ============================================================================
# Required for JSON parsing in MLC-LLM protocol

-keepattributes *Annotation*
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class kotlinx.serialization.** { *; }

# Keep serializable classes
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}

# ============================================================================
# Hilt / Dagger
# ============================================================================
# Hilt uses reflection for dependency injection

-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ComponentSupplier { *; }

# ============================================================================
# Coroutines
# ============================================================================
# Prevent stripping of coroutine internals

-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# ============================================================================
# Protobuf (DataStore)
# ============================================================================

-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }

# ============================================================================
# Debugging - Keep source file and line numbers for stack traces
# ============================================================================

-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

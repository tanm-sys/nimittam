/*
 * Copyright 2025 Tanmay Patil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.protobuf)
  alias(libs.plugins.hilt.application)
  alias(libs.plugins.ksp)
  alias(libs.plugins.compose.compiler)
}

android {
  namespace = "com.google.ai.edge.gallery"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.google.aiedge.gallery"
    minSdk = 31
    targetSdk = 36
    versionCode = 18
    versionName = "1.1.0"

    manifestPlaceholders["applicationName"] = "com.google.ai.edge.gallery.GalleryApplication"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  // Prevent compression of embedded model files
  // MLC-LLM model weights (.bin), tokenizer (.json), and config files must not be compressed
  androidResources {
    noCompress += listOf("task", "bin", "json", "txt")
  }

  buildTypes {
    release {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro"
      )
      signingConfig = signingConfigs.getByName("debug")
    }
  }

  // Required for large native libraries (112MB libtvm4j_runtime_packed.so)
  // Android 12+ defaults to extractNativeLibs=false which can cause loading issues
  packaging {
    jniLibs {
      useLegacyPackaging = true
    }
  }

  // ABI splits for optimized APK size per architecture
  splits {
    abi {
      isEnable = true
      reset()
      // Only arm64-v8a is supported by the MLC-LLM prebuilt library
      include("arm64-v8a")
      isUniversalApk = false
    }
  }

  // NDK configuration for optimal LLM performance
  ndkVersion = "27.2.12479018"

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  
  kotlin {
    compilerOptions {
      jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
  }
  
  buildFeatures {
    buildConfig = true
    compose = true
  }

  composeOptions {
    kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get()
  }
}

dependencies {
  // MLC-LLM runtime library
  implementation(project(":mlc4j"))
  
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.kotlin.reflect)
  implementation(libs.androidx.work.runtime)
  implementation(libs.androidx.datastore)
  implementation(libs.com.google.code.gson)
  implementation(libs.androidx.lifecycle.process)
  implementation(libs.androidx.security.crypto)
  implementation(libs.androidx.exifinterface)

  implementation(libs.kotlinx.collections.immutable)

  // Room Database for chat history persistence
  implementation(libs.room.runtime)
  implementation(libs.room.ktx)
  ksp(libs.room.compiler)

  implementation(libs.protobuf.javalite)
  implementation(libs.hilt.android)
  ksp(libs.hilt.android.compiler)

  // === Jetpack Compose ===
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.icons)
  implementation(libs.androidx.compose.animation)
  implementation(libs.androidx.compose.animation.graphics)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.hilt.navigation.compose)

  debugImplementation(libs.androidx.compose.ui.tooling)

  testImplementation(libs.junit)
  testImplementation(libs.robolectric)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.turbine)
  testImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.hilt.android.testing)
}

protobuf {
  protoc { artifact = "com.google.protobuf:protoc:4.29.3" }
  generateProtoTasks { all().forEach { it.plugins { create("java") { option("lite") } } } }
}

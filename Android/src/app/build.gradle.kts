/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
  androidResources {
    noCompress += listOf("task")
  }

  buildTypes {
    release {
      isMinifyEnabled = true
      isShrinkResources = true
      signingConfig = signingConfigs.getByName("debug")
    }
  }

  // ABI splits for optimized APK size per architecture
  splits {
    abi {
      isEnable = true
      reset()
      include("arm64-v8a", "armeabi-v7a", "x86_64")
      isUniversalApk = true
    }
  }

  // Native build configuration for MLC-LLM
  externalNativeBuild {
    cmake {
      path = file("src/main/cpp/CMakeLists.txt")
      version = "3.22.1"
    }
  }

  // NDK configuration for optimal LLM performance
  ndkVersion = "27.2.12479018"
  
  defaultConfig {
    ndk {
      // Target modern ARM64 devices for best LLM performance
      abiFilters += listOf("arm64-v8a")
    }
  }

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
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.hilt.android.testing)
}

protobuf {
  protoc { artifact = "com.google.protobuf:protoc:4.29.3" }
  generateProtoTasks { all().forEach { it.plugins { create("java") { option("lite") } } } }
}

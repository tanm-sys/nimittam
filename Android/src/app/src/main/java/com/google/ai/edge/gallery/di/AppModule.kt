/*
 * Copyright 2025 Tanmay Patil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.google.ai.edge.gallery.di

import android.content.Context
import android.util.Log
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import com.google.ai.edge.gallery.AppLifecycleProvider
import com.google.ai.edge.gallery.GalleryLifecycleProvider
import com.google.ai.edge.gallery.SettingsSerializer
import com.google.ai.edge.gallery.UserDataSerializer
import com.google.ai.edge.gallery.common.HapticFeedbackManager
import com.google.ai.edge.gallery.data.DataStoreRepository
import com.google.ai.edge.gallery.data.DefaultDataStoreRepository
import com.google.ai.edge.gallery.llm.ModelManager
import com.google.ai.edge.gallery.proto.Settings
import com.google.ai.edge.gallery.proto.UserData
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object AppModule {

  private const val TAG = "AppModule"

  // Provides the SettingsSerializer
  @Provides
  @Singleton
  fun provideSettingsSerializer(): Serializer<Settings> {
    return SettingsSerializer
  }

  // Provides the UserDataSerializer
  @Provides
  @Singleton
  fun provideUserDataSerializer(): Serializer<UserData> {
    return UserDataSerializer
  }

  // Provides DataStore<Settings> with corruption handling
  @Provides
  @Singleton
  fun provideSettingsDataStore(
    @ApplicationContext context: Context,
    settingsSerializer: Serializer<Settings>,
  ): DataStore<Settings> {
    return DataStoreFactory.create(
      serializer = settingsSerializer,
      produceFile = { context.dataStoreFile("settings.pb") },
      corruptionHandler = ReplaceFileCorruptionHandler { corruptionException ->
        Log.e(TAG, "Settings DataStore corrupted, replacing with default", corruptionException)
        Settings.getDefaultInstance()
      }
    )
  }

  // Provides DataStore<UserData> with corruption handling
  @Provides
  @Singleton
  fun provideUserDataDataStore(
    @ApplicationContext context: Context,
    userDataSerializer: Serializer<UserData>,
  ): DataStore<UserData> {
    val operationId = java.util.UUID.randomUUID().toString().take(8)
    Log.d(TAG, "[DIAGNOSTIC] provideUserDataDataStore[$operationId] Creating UserData DataStore")

    val dataStoreFile = context.dataStoreFile("user_data.pb")
    Log.d(TAG, "[DIAGNOSTIC] provideUserDataDataStore[$operationId] DataStore file: ${dataStoreFile.absolutePath}")

    return DataStoreFactory.create(
      serializer = userDataSerializer,
      produceFile = { dataStoreFile },
      corruptionHandler = ReplaceFileCorruptionHandler { corruptionException ->
        Log.e(TAG, "[DIAGNOSTIC] provideUserDataDataStore[$operationId] DataStore corrupted, replacing with default", corruptionException)
        UserData.getDefaultInstance()
      }
    )
  }

  // Provides AppLifecycleProvider
  @Provides
  @Singleton
  fun provideAppLifecycleProvider(): AppLifecycleProvider {
    return GalleryLifecycleProvider()
  }

  // Provides DataStoreRepository with full implementation
  @Provides
  @Singleton
  fun provideDataStoreRepository(
    dataStore: DataStore<Settings>,
    userDataDataStore: DataStore<UserData>,
  ): DataStoreRepository {
    return DefaultDataStoreRepository(dataStore, userDataDataStore)
  }

  // Provides HapticFeedbackManager for haptic feedback throughout the app
  @Provides
  @Singleton
  fun provideHapticFeedbackManager(
    @ApplicationContext context: Context,
  ): HapticFeedbackManager {
    return HapticFeedbackManager(context)
  }

  // Provides ModelManager for model downloads and management
  @Provides
  @Singleton
  fun provideModelManager(
    @ApplicationContext context: Context,
  ): ModelManager {
    return ModelManager(context)
  }
}

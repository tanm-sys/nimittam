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

package com.google.ai.edge.gallery.data

import androidx.datastore.core.DataStore
import com.google.ai.edge.gallery.proto.Settings
import com.google.ai.edge.gallery.proto.UserData

// UI-related data store operations removed
interface DataStoreRepository {
  // Placeholder - all UI-related methods removed
}

/** Repository for managing data using Proto DataStore. */
class DefaultDataStoreRepository(
  private val dataStore: DataStore<Settings>,
  private val userDataDataStore: DataStore<UserData>,
) : DataStoreRepository {
  // All UI-related implementations removed
}

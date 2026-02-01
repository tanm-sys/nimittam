/*
 * Copyright 2025-2026 Google LLC
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

package com.google.ai.edge.gallery.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.google.ai.edge.gallery.data.db.entity.ConversationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    
    @Query("SELECT * FROM conversations WHERE isArchived = 0 ORDER BY updatedAt DESC")
    fun getAllConversations(): Flow<List<ConversationEntity>>
    
    @Query("SELECT * FROM conversations WHERE isArchived = 1 ORDER BY updatedAt DESC")
    fun getArchivedConversations(): Flow<List<ConversationEntity>>
    
    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getConversationById(id: String): ConversationEntity?
    
    @Query("SELECT * FROM conversations WHERE id = :id")
    fun observeConversationById(id: String): Flow<ConversationEntity?>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: ConversationEntity)
    
    @Update
    suspend fun updateConversation(conversation: ConversationEntity)
    
    @Delete
    suspend fun deleteConversation(conversation: ConversationEntity)
    
    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteConversationById(id: String)
    
    @Query("UPDATE conversations SET updatedAt = :timestamp, previewText = :preview, messageCount = messageCount + 1 WHERE id = :id")
    suspend fun updateConversationPreview(id: String, preview: String, timestamp: Long = System.currentTimeMillis())
    
    @Query("UPDATE conversations SET isArchived = :archived WHERE id = :id")
    suspend fun setArchived(id: String, archived: Boolean)
    
    @Query("SELECT COUNT(*) FROM conversations WHERE isArchived = 0")
    fun getConversationCount(): Flow<Int>
}

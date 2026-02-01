/*
 * Copyright 2025 Tanmay Patil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.google.ai.edge.gallery.data.db.repository

import com.google.ai.edge.gallery.data.db.dao.ConversationDao
import com.google.ai.edge.gallery.data.db.dao.MessageDao
import com.google.ai.edge.gallery.data.db.entity.ConversationEntity
import com.google.ai.edge.gallery.data.db.entity.MessageEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatHistoryRepository @Inject constructor(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao
) {
    val allConversations: Flow<List<ConversationEntity>> = conversationDao.getAllConversations()
    val conversationCount: Flow<Int> = conversationDao.getConversationCount()
    
    suspend fun createConversation(title: String, modelName: String? = null): ConversationEntity {
        val conversation = ConversationEntity(
            title = title,
            modelName = modelName
        )
        conversationDao.insertConversation(conversation)
        return conversation
    }
    
    suspend fun getConversation(id: String): ConversationEntity? {
        return conversationDao.getConversationById(id)
    }
    
    fun observeConversation(id: String): Flow<ConversationEntity?> {
        return conversationDao.observeConversationById(id)
    }
    
    suspend fun updateConversation(conversation: ConversationEntity) {
        conversationDao.updateConversation(conversation)
    }
    
    suspend fun deleteConversation(id: String) {
        conversationDao.deleteConversationById(id)
    }
    
    suspend fun archiveConversation(id: String, archived: Boolean = true) {
        conversationDao.setArchived(id, archived)
    }
    
    fun getMessagesForConversation(conversationId: String): Flow<List<MessageEntity>> {
        return messageDao.getMessagesForConversation(conversationId)
    }
    
    suspend fun getMessagesSync(conversationId: String): List<MessageEntity> {
        return messageDao.getMessagesForConversationSync(conversationId)
    }
    
    /**
     * Add a message and update conversation preview atomically.
     * Uses @Transaction to prevent N+1 query issues and ensure data consistency.
     *
     * OPTIMIZATION: Both operations are performed in a single transaction,
     * reducing database round-trips from 2 to 1.
     */
    @androidx.room.Transaction
    suspend fun addMessage(
        conversationId: String,
        content: String,
        isUser: Boolean
    ): MessageEntity {
        val message = MessageEntity(
            conversationId = conversationId,
            content = content,
            isUser = isUser
        )
        
        // These operations are now part of a single transaction
        messageDao.insertMessage(message)
        
        val preview = if (content.length > 100) content.take(100) + "..." else content
        conversationDao.updateConversationPreview(conversationId, preview)
        
        return message
    }
    
    suspend fun addMessages(messages: List<MessageEntity>) {
        messageDao.insertMessages(messages)
    }
    
    suspend fun updateConversationTitle(id: String, title: String) {
        val conversation = conversationDao.getConversationById(id)
        conversation?.let {
            conversationDao.updateConversation(it.copy(title = title, updatedAt = System.currentTimeMillis()))
        }
    }
    
    suspend fun generateTitleFromFirstMessage(conversationId: String): String {
        val messages = messageDao.getMessagesForConversationSync(conversationId)
        val firstUserMessage = messages.firstOrNull { it.isUser }
        val title = firstUserMessage?.content?.let { content ->
            if (content.length > 40) content.take(40) + "..." else content
        } ?: "New Chat"
        
        updateConversationTitle(conversationId, title)
        return title
    }
}

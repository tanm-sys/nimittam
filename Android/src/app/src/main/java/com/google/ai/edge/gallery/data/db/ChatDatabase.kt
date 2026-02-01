/*
 * Copyright 2025 Tanmay Patil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.google.ai.edge.gallery.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.google.ai.edge.gallery.data.db.dao.ConversationDao
import com.google.ai.edge.gallery.data.db.dao.MessageDao
import com.google.ai.edge.gallery.data.db.entity.ConversationEntity
import com.google.ai.edge.gallery.data.db.entity.MessageEntity

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    
    companion object {
        const val DATABASE_NAME = "nimittam_chat.db"
    }
}

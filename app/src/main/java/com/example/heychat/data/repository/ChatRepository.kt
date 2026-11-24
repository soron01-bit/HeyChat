package com.example.heychat.data.repository

import com.example.heychat.data.models.MessageModel
import com.example.heychat.data.models.MessageReactionModel
import com.example.heychat.data.models.Profile
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun observeMessages(conversationId: String): Flow<List<MessageModel>>
    fun observeReactionsForMessages(messageIds: List<String>): Flow<Map<String, List<MessageReactionModel>>>
    suspend fun toggleReaction(messageId: String, emoji: String): Result<Unit>
    suspend fun syncNewMessages(conversationId: String): Result<Unit>
    suspend fun runSafetyNetSync(conversationId: String): Result<Unit>
    suspend fun cleanupOldMessages(): Result<Unit>
    suspend fun getConversationParticipant(conversationId: String, currentUserId: String): Result<Profile>
    suspend fun getMessages(conversationId: String): Result<List<MessageModel>>
    suspend fun sendMessage(conversationId: String, senderId: String, text: String, replyToMessageId: String? = null): Result<Unit>
    suspend fun sendPhotoMessage(conversationId: String, senderId: String, imageBytes: ByteArray, replyToMessageId: String? = null): Result<Unit>
    suspend fun sendVoiceMessage(conversationId: String, senderId: String, audioBytes: ByteArray, durationSec: Int, replyToMessageId: String? = null): Result<Unit>
    suspend fun markMessagesAsSeen(conversationId: String, currentUserId: String): Result<Unit>
    suspend fun deleteMessageForMe(messageId: String): Result<Unit>
    suspend fun deleteMessageForEveryone(messageId: String): Result<Unit>
    suspend fun deleteMessagesForMeBulk(messageIds: List<String>): Result<Unit>
    suspend fun deleteMessagesForEveryoneBulk(messageIds: List<String>): Result<Unit>
    fun subscribeToMessages(conversationId: String): Flow<MessageModel>
}

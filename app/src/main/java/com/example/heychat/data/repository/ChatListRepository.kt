package com.example.heychat.data.repository

import com.example.heychat.data.models.UserConversation
import kotlinx.coroutines.flow.Flow

interface ChatListRepository {
    fun observeConversations(): Flow<List<UserConversation>>
    fun observeConversationById(conversationId: String): Flow<UserConversation?>
    suspend fun syncUserConversations(currentUserId: String): Result<Unit>
    suspend fun saveConversationToRoom(conversation: UserConversation)
    suspend fun getUserConversations(currentUserId: String): Result<List<UserConversation>>
    suspend fun toggleMuteConversation(conversationId: String, isMuted: Boolean): Result<Unit>
    suspend fun toggleFavoriteConversation(conversationId: String, isFavorite: Boolean): Result<Unit>
    suspend fun toggleFavoriteConversationsBulk(conversationIds: List<String>, isFavorite: Boolean): Result<Unit>
    suspend fun deleteConversation(conversationId: String): Result<Unit>
    fun subscribeToUserChatUpdates(currentUserId: String): Flow<Unit>
}

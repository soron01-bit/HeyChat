package com.example.heychat.data.repository

import com.example.heychat.data.models.SearchResultUser

interface SearchRepository {
    suspend fun searchUsers(query: String, currentUserId: String, limit: Int = 20, offset: Int = 0): Result<List<SearchResultUser>>
    suspend fun getOrCreateConversation(currentUserId: String, otherUserId: String): Result<String>
}

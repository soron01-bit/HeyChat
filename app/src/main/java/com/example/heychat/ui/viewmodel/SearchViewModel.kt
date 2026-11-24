package com.example.heychat.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.heychat.data.models.SearchResultUser
import com.example.heychat.data.models.UserConversation
import com.example.heychat.data.remote.supabaseClient
import com.example.heychat.data.repository.ChatListRepository
import com.example.heychat.data.repository.ChatListRepositoryImpl
import com.example.heychat.data.repository.SearchRepository
import com.example.heychat.data.repository.SearchRepositoryImpl
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

sealed interface SearchUiState {
    data object Idle : SearchUiState
    data object Loading : SearchUiState
    data object Empty : SearchUiState
    data class Success(val results: List<SearchResultUser>) : SearchUiState
    data class Error(val message: String) : SearchUiState
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val searchRepository: SearchRepository = SearchRepositoryImpl(application)
    private val chatListRepository: ChatListRepository = ChatListRepositoryImpl(application)

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _searchUiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val searchUiState: StateFlow<SearchUiState> = _searchUiState.asStateFlow()

    private val _startChatLoading = MutableStateFlow(false)
    val startChatLoading: StateFlow<Boolean> = _startChatLoading.asStateFlow()

    init {
        viewModelScope.launch {
            _query
                .debounce(350L)
                .distinctUntilChanged()
                .flatMapLatest { searchQuery ->
                    flow {
                        val currentUserId = supabaseClient.auth.currentUserOrNull()?.id ?: ""
                        if (searchQuery.isBlank()) {
                            emit(SearchUiState.Idle)
                        } else {
                            emit(SearchUiState.Loading)
                            val result = searchRepository.searchUsers(
                                query = searchQuery.trim(),
                                currentUserId = currentUserId
                            )
                            result.onSuccess { list ->
                                if (list.isEmpty()) {
                                    emit(SearchUiState.Empty)
                                } else {
                                    emit(SearchUiState.Success(list))
                                }
                            }.onFailure { throwable ->
                                emit(SearchUiState.Error(throwable.message ?: "Failed to search users"))
                            }
                        }
                    }
                }
                .collect { state ->
                    _searchUiState.value = state
                }
        }
    }

    fun onQueryChanged(newQuery: String) {
        _query.value = newQuery
    }

    fun startChatWithUser(user: SearchResultUser, onConversationReady: (String) -> Unit) {
        val currentUserId = supabaseClient.auth.currentUserOrNull()?.id ?: return
        viewModelScope.launch {
            _startChatLoading.value = true
            val result = searchRepository.getOrCreateConversation(currentUserId, user.id)
            _startChatLoading.value = false
            result.onSuccess { conversationId ->
                // Navigate to chat screen WITHOUT creating an empty conversation in Room DB
                onConversationReady(conversationId)
            }.onFailure { throwable ->
                _searchUiState.value = SearchUiState.Error("Could not start conversation: ${throwable.message}")
            }
        }
    }
}

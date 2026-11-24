package com.example.heychat.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.heychat.data.local.entities.BlockedUserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockedUserDao {

    @Query("SELECT blocked_id FROM local_blocked_users")
    fun getBlockedUserIdsFlow(): Flow<List<String>>

    @Query("SELECT blocked_id FROM local_blocked_users")
    suspend fun getBlockedUserIdsOnce(): List<String>

    @Query("SELECT EXISTS(SELECT 1 FROM local_blocked_users WHERE blocked_id = :blockedId)")
    suspend fun isUserBlocked(blockedId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun blockUserLocally(entity: BlockedUserEntity)

    @Query("DELETE FROM local_blocked_users WHERE blocked_id = :blockedId")
    suspend fun unblockUserLocally(blockedId: String)

    @Query("DELETE FROM local_blocked_users")
    suspend fun clearAllBlockedUsersLocally()
}

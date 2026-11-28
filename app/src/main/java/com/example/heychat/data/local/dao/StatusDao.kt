package com.example.heychat.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.heychat.data.local.entities.StatusEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StatusDao {

    @Query("SELECT * FROM statuses ORDER BY created_at DESC")
    fun getAllStatuses(): Flow<List<StatusEntity>>

    @Query("SELECT * FROM statuses")
    suspend fun getAllStatusesOnce(): List<StatusEntity>

    @Query("SELECT * FROM statuses WHERE user_id = :userId ORDER BY created_at DESC")
    fun getStatusesForUser(userId: String): Flow<List<StatusEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStatuses(statuses: List<StatusEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStatus(status: StatusEntity)

    @Query("UPDATE statuses SET is_viewed = 1 WHERE id = :statusId")
    suspend fun markStatusAsViewed(statusId: String)

    @Query("UPDATE statuses SET local_media_path = :localPath WHERE id = :statusId")
    suspend fun updateLocalMediaPath(statusId: String, localPath: String)

    @Query("DELETE FROM statuses WHERE LOWER(id) = LOWER(:statusId) OR id = :statusId")
    suspend fun deleteStatusById(statusId: String)

    @Query("DELETE FROM statuses WHERE expires_at IS NOT NULL AND expires_at != '' AND expires_at < :nowIso")
    suspend fun deleteExpiredStatuses(nowIso: String)

    @Query("DELETE FROM statuses")
    suspend fun clearAllStatuses()
}

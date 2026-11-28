package com.example.heychat.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.heychat.data.local.entities.ProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {

    @Query("SELECT * FROM profiles WHERE LOWER(id) = LOWER(:userId) OR id = :userId")
    fun getProfileFlow(userId: String): Flow<ProfileEntity?>

    @Query("SELECT * FROM profiles WHERE LOWER(id) = LOWER(:userId) OR id = :userId")
    suspend fun getProfile(userId: String): ProfileEntity?

    @Query("SELECT * FROM profiles ORDER BY full_name ASC")
    suspend fun getAllProfilesOnce(): List<ProfileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: ProfileEntity)

    @Query("DELETE FROM profiles")
    suspend fun clearAllProfiles()
}

package com.example.heychat.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.heychat.data.local.dao.BlockedUserDao
import com.example.heychat.data.local.dao.ConversationDao
import com.example.heychat.data.local.dao.DeletedConversationDao
import com.example.heychat.data.local.dao.HiddenMessageDao
import com.example.heychat.data.local.dao.MessageDao
import com.example.heychat.data.local.dao.MessageReactionDao
import com.example.heychat.data.local.dao.ProfileDao
import com.example.heychat.data.local.dao.StatusDao
import com.example.heychat.data.local.entities.BlockedUserEntity
import com.example.heychat.data.local.entities.DeletedConversationEntity
import com.example.heychat.data.local.entities.HiddenMessageEntity
import com.example.heychat.data.local.entities.MessageEntity
import com.example.heychat.data.local.entities.MessageReactionEntity
import com.example.heychat.data.local.entities.ProfileEntity
import com.example.heychat.data.local.entities.StatusEntity
import com.example.heychat.data.local.entities.UserConversationEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Database(
    entities = [
        MessageEntity::class,
        UserConversationEntity::class,
        ProfileEntity::class,
        DeletedConversationEntity::class,
        BlockedUserEntity::class,
        HiddenMessageEntity::class,
        MessageReactionEntity::class,
        StatusEntity::class
    ],
    version = 17,
    exportSchema = false
)
abstract class HeyChatDatabase : RoomDatabase() {

    abstract fun messageDao(): MessageDao
    abstract fun conversationDao(): ConversationDao
    abstract fun profileDao(): ProfileDao
    abstract fun deletedConversationDao(): DeletedConversationDao
    abstract fun blockedUserDao(): BlockedUserDao
    abstract fun hiddenMessageDao(): HiddenMessageDao
    abstract fun messageReactionDao(): MessageReactionDao
    abstract fun statusDao(): StatusDao

    suspend fun clearDatabase() = withContext(Dispatchers.IO) {
        clearAllTables()
    }

    companion object {
        @Volatile
        private var INSTANCE: HeyChatDatabase? = null

        fun getInstance(context: Context): HeyChatDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    HeyChatDatabase::class.java,
                    "heychat_offline_db"
                )
                    .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                    .addCallback(object : Callback() {
                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            runCatching {
                                db.execSQL("PRAGMA synchronous = NORMAL;")
                                db.execSQL("PRAGMA busy_timeout = 5000;")
                            }
                        }
                    })
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

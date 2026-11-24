package com.jinwoo.ai.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: String,
    val content: String,
    val createdAt: Long,
)

@Dao
interface MessageDao {
    @Insert suspend fun insert(m: MessageEntity)
    @Query("SELECT * FROM messages ORDER BY id DESC LIMIT :n") suspend fun recent(n: Int): List<MessageEntity>
    @Query("DELETE FROM messages") suspend fun clear()
}

@Database(entities = [MessageEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messages(): MessageDao
}

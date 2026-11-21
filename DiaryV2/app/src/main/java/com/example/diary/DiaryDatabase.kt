
package com.example.diary

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "documents")
data class DiaryDocument(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",
    val content: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = System.currentTimeMillis(),
    val isNamed: Boolean = false
)

@Dao
interface DiaryDao {
    @Query("SELECT * FROM documents ORDER BY modifiedAt DESC")
    fun observeAll(): Flow<List<DiaryDocument>>

    @Query("SELECT * FROM documents WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): DiaryDocument?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(document: DiaryDocument): Long

    @Query("""
        UPDATE documents
        SET title = :title, content = :content, modifiedAt = :modifiedAt, isNamed = :isNamed
        WHERE id = :id
    """)
    suspend fun update(
        id: Long,
        title: String,
        content: String,
        modifiedAt: Long,
        isNamed: Boolean
    )

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("""
        DELETE FROM documents
        WHERE isNamed = 0 AND content = ''
    """)
    suspend fun deleteEmptyUnnamedDrafts()
}

@Database(
    entities = [DiaryDocument::class],
    version = 1,
    exportSchema = false
)
abstract class DiaryDatabase : RoomDatabase() {
    abstract fun diaryDao(): DiaryDao

    companion object {
        @Volatile
        private var INSTANCE: DiaryDatabase? = null

        fun get(context: Context): DiaryDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    DiaryDatabase::class.java,
                    "my_diary_v2.db"
                ).build().also { INSTANCE = it }
            }
    }
}

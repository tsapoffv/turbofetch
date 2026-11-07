package com.turbofetch.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FileDao {

    @Query("SELECT * FROM files ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<FileEntity>>

    @Query("SELECT * FROM files WHERE id = :id")
    suspend fun get(id: String): FileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(file: FileEntity)

    @Query("DELETE FROM files WHERE id = :id")
    suspend fun delete(id: String)

    /** Удалить все записи разом. */
    @Query("DELETE FROM files")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM files")
    suspend fun count(): Int
}

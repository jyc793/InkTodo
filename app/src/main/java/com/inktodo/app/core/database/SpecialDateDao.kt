package com.inktodo.app.core.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SpecialDateDao {
    @Query("SELECT * FROM special_dates ORDER BY updated_at_epoch_millis DESC")
    fun observeAll(): Flow<List<SpecialDateEntity>>

    @Query("SELECT * FROM special_dates ORDER BY updated_at_epoch_millis DESC")
    suspend fun getAll(): List<SpecialDateEntity>

    @Query("SELECT * FROM special_dates WHERE id = :id")
    suspend fun findById(id: Long): SpecialDateEntity?

    @Upsert
    suspend fun upsert(value: SpecialDateEntity): Long

    @Delete
    suspend fun delete(value: SpecialDateEntity)
}

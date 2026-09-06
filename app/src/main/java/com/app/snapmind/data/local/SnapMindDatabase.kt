package com.app.snapmind.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.app.snapmind.data.local.dao.CapturedItemDao
import com.app.snapmind.data.local.entity.CapturedItemEntity

@Database(
    entities = [CapturedItemEntity::class],
    version = 4,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class SnapMindDatabase : RoomDatabase() {
    abstract fun capturedItemDao(): CapturedItemDao

    companion object {
        const val NAME = "snapmind.db"
    }
}

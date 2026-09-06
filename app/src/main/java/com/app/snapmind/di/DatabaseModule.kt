package com.app.snapmind.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.app.snapmind.data.local.SnapMindDatabase
import com.app.snapmind.data.local.dao.CapturedItemDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * Adds `resolution` (spec.md 11.8): DONE/DISCARDED/null, distinct from `resolvedAt`
     * (when, not how). Existing resolved rows are backfilled as DONE -- a gentler assumption
     * than DISCARDED for data that predates the distinction.
     */
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE captured_items ADD COLUMN resolution TEXT")
            db.execSQL("UPDATE captured_items SET resolution = 'DONE' WHERE resolvedAt IS NOT NULL")
        }
    }
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE captured_items ADD COLUMN detectedCategory TEXT")
            db.execSQL("ALTER TABLE captured_items ADD COLUMN detectedDateMillis INTEGER")
        }
    }

    /** Adds `userCategory` (spec.md 11.16): the category the user set by hand, if any. */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE captured_items ADD COLUMN userCategory TEXT")
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SnapMindDatabase =
        Room.databaseBuilder(context, SnapMindDatabase::class.java, SnapMindDatabase.NAME)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .build()

    @Provides
    fun provideCapturedItemDao(db: SnapMindDatabase): CapturedItemDao = db.capturedItemDao()
}

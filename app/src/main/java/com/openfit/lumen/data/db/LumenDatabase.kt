package com.openfit.lumen.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [DataPointEntity::class, SyncStateEntity::class], version = 3, exportSchema = true)
abstract class LumenDatabase : RoomDatabase() {
  abstract fun healthDao(): HealthDao

  companion object {
    fun create(context: Context): LumenDatabase =
      // Everything here is a copy of Health Connect data, so a schema change just
      // drops the copy and the next sync rebuilds it.
      Room.databaseBuilder(context, LumenDatabase::class.java, "lumen.db")
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()
  }
}

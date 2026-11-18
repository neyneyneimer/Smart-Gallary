package ai.sakana.aura.gallery.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [MediaItemEntity::class], version = 1, exportSchema = true)
abstract class AuraGalleryDatabase : RoomDatabase() {
    abstract fun mediaItemDao(): MediaItemDao
}

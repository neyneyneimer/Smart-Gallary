package com.example.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoDao {
    @Query("SELECT * FROM photos ORDER BY dateTaken DESC")
    fun getAllPhotos(): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos WHERE id = :id")
    suspend fun getPhotoById(id: Long): PhotoEntity?

    @Query("SELECT * FROM photos WHERE originalContentHash = :hash LIMIT 1")
    suspend fun getPhotoByHash(hash: String): PhotoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhoto(photo: PhotoEntity): Long

    @Update
    suspend fun updatePhoto(photo: PhotoEntity)

    @Delete
    suspend fun deletePhoto(photo: PhotoEntity)

    @Query("SELECT * FROM photo_versions WHERE photoId = :photoId ORDER BY versionNumber DESC")
    fun getVersionsForPhoto(photoId: Long): Flow<List<PhotoEditVersionEntity>>

    @Query("SELECT * FROM photo_versions WHERE photoId = :photoId ORDER BY versionNumber DESC")
    suspend fun getVersionsForPhotoSync(photoId: Long): List<PhotoEditVersionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVersion(version: PhotoEditVersionEntity): Long

    @Query("DELETE FROM photo_versions WHERE photoId = :photoId")
    suspend fun deleteVersionsForPhoto(photoId: Long)

    @Query("DELETE FROM photo_versions WHERE photoId = :photoId AND versionNumber = :versionNumber")
    suspend fun deleteVersion(photoId: Long, versionNumber: Int)
}

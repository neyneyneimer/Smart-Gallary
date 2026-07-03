package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "photos")
data class PhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val resourceName: String, // e.g. "img_face_alice", "img_face_bob", "img_face_clara", "img_scenic_mountain", "img_scenic_lake"
    val title: String,
    val sizeBytes: Long,
    val dateTaken: Long,
    val mimeType: String = "image/jpeg",
    val durationSeconds: Int = 0, // 0 if it's an image, >0 if it's a video duration in seconds
    val faceTag: String? = null, // "Alice", "Bob", "Clara", or null
    val originalContentHash: String, // to prevent duplicates
    val hasBackupSynced: Boolean = false,
    val backupLocation: String? = null, // "Google Drive", "OneDrive", "Dropbox"
    val currentFilter: String? = null, // Current active non-destructive filter, e.g. "Vintage", "Noir", "Warm", or null (Original)
    val attire: String? = null, // description of clothes/wear, e.g. "White Shirt & Glasses", "Formal Dark Blazer"
    val environment: String? = null, // e.g. "Outdoor Sunny Park", "Professional Media Studio"
    val lighting: String? = null // e.g. "Natural Bright Daylight", "Cool Ring Lighting"
)

@Entity(tableName = "photo_versions")
data class PhotoEditVersionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val photoId: Long, // foreign key reference to photos.id
    val versionNumber: Int, // 1, 2, 3...
    val filterName: String, // filter name applied at this version
    val timestamp: Long,
    val sizeDifferenceBytes: Long = 0 // we use a mock non-destructive state which takes almost 0 extra bytes!
)

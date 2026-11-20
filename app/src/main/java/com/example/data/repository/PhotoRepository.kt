package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.R
import com.example.data.api.GeminiAnalyzer
import com.example.data.database.PhotoDao
import com.example.data.database.PhotoEntity
import com.example.data.database.PhotoEditVersionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class PhotoRepository(
    private val context: Context,
    private val photoDao: PhotoDao
) {
    private val TAG = "PhotoRepository"

    val allPhotos: Flow<List<PhotoEntity>> = photoDao.getAllPhotos()

    suspend fun getPhotoById(id: Long): PhotoEntity? = photoDao.getPhotoById(id)

    fun getVersionsForPhoto(photoId: Long): Flow<List<PhotoEditVersionEntity>> = 
        photoDao.getVersionsForPhoto(photoId)

    // Populate initial images and videos if the DB is empty
    suspend fun checkAndPrepopulate() = withContext(Dispatchers.IO) {
        val currentPhotos = allPhotos.first()
        if (currentPhotos.isEmpty()) {
            Log.d(TAG, "Database is empty. Pre-populating default images and videos...")
            
            val defaults = listOf(
                PhotoEntity(
                    resourceName = "img_face_alice",
                    title = "Alice's Portrait",
                    sizeBytes = 2400000L,
                    dateTaken = System.currentTimeMillis() - 3600000 * 12,
                    originalContentHash = computeMockHash("img_face_alice"),
                    faceTag = null,
                    mimeType = "image/jpeg",
                    durationSeconds = 0
                ),
                PhotoEntity(
                    resourceName = "img_face_bob",
                    title = "Bob's Headshot",
                    sizeBytes = 1850000L,
                    dateTaken = System.currentTimeMillis() - 3600000 * 10,
                    originalContentHash = computeMockHash("img_face_bob"),
                    faceTag = null,
                    mimeType = "image/jpeg",
                    durationSeconds = 0
                ),
                PhotoEntity(
                    resourceName = "img_face_clara",
                    title = "Clara's Headshot",
                    sizeBytes = 2100000L,
                    dateTaken = System.currentTimeMillis() - 3600000 * 8,
                    originalContentHash = computeMockHash("img_face_clara"),
                    faceTag = null,
                    mimeType = "image/jpeg",
                    durationSeconds = 0
                ),
                PhotoEntity(
                    resourceName = "img_scenic_mountain",
                    title = "Golden Mountain Sunset",
                    sizeBytes = 4500000L,
                    dateTaken = System.currentTimeMillis() - 3600000 * 6,
                    originalContentHash = computeMockHash("img_scenic_mountain"),
                    faceTag = null,
                    mimeType = "image/jpeg",
                    durationSeconds = 0
                ),
                PhotoEntity(
                    resourceName = "img_scenic_lake",
                    title = "Serene Pine Lake",
                    sizeBytes = 3900000L,
                    dateTaken = System.currentTimeMillis() - 3600000 * 4,
                    originalContentHash = computeMockHash("img_scenic_lake"),
                    faceTag = null,
                    mimeType = "image/jpeg",
                    durationSeconds = 0
                ),
                PhotoEntity(
                    resourceName = "img_scenic_mountain", // Use mountain image as thumbnail for video
                    title = "Mountain Sunset Flyby.mp4",
                    sizeBytes = 15400000L, // ~15.4MB
                    dateTaken = System.currentTimeMillis() - 3600000 * 2,
                    originalContentHash = computeMockHash("vid_sunset_flyby"),
                    faceTag = null,
                    mimeType = "video/mp4",
                    durationSeconds = 24
                ),
                PhotoEntity(
                    resourceName = "img_face_alice", // Use Alice's image as thumbnail for video
                    title = "Alice Interview Clip.mp4",
                    sizeBytes = 28600000L, // ~28.6MB
                    dateTaken = System.currentTimeMillis() - 3600000 * 1,
                    originalContentHash = computeMockHash("vid_alice_interview"),
                    faceTag = null,
                    mimeType = "video/mp4",
                    durationSeconds = 45
                ),
                PhotoEntity(
                    resourceName = "img_face_alice",
                    title = "Alice Park Jogging.mp4",
                    sizeBytes = 11200000L,
                    dateTaken = System.currentTimeMillis() - 3600000 * 3,
                    originalContentHash = computeMockHash("vid_alice_jogging"),
                    faceTag = null,
                    mimeType = "video/mp4",
                    durationSeconds = 15
                ),
                PhotoEntity(
                    resourceName = "img_face_alice",
                    title = "Alice Studio Portrait.jpg",
                    sizeBytes = 2800000L,
                    dateTaken = System.currentTimeMillis() - 3600000 * 5,
                    originalContentHash = computeMockHash("img_alice_studio"),
                    faceTag = null,
                    mimeType = "image/jpeg",
                    durationSeconds = 0
                ),
                PhotoEntity(
                    resourceName = "img_face_bob",
                    title = "Bob Coffee Vlog.mp4",
                    sizeBytes = 13800000L,
                    dateTaken = System.currentTimeMillis() - 3600000 * 7,
                    originalContentHash = computeMockHash("vid_bob_coffee"),
                    faceTag = null,
                    mimeType = "video/mp4",
                    durationSeconds = 20
                ),
                PhotoEntity(
                    resourceName = "img_face_bob",
                    title = "Bob Office Presentation.mp4",
                    sizeBytes = 34500000L,
                    dateTaken = System.currentTimeMillis() - 3600000 * 9,
                    originalContentHash = computeMockHash("vid_bob_presentation"),
                    faceTag = null,
                    mimeType = "video/mp4",
                    durationSeconds = 50
                )
            )

            defaults.forEach { entity ->
                photoDao.insertPhoto(entity)
            }
            Log.d(TAG, "Pre-population complete with images and videos.")
        }
    }

    // AI recognition logic: scan all photos that have no faceTag yet
    suspend fun runAIFaceScanner(onProgress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        val photos = allPhotos.first()
        val unscanned = photos.filter { it.faceTag == null }
        if (unscanned.isEmpty()) return@withContext

        val total = unscanned.size.toFloat()
        unscanned.forEachIndexed { index, photo ->
            // Update progress callback
            onProgress((index + 1) / total)
            
            // Get drawable resource ID dynamically
            val resId = getDrawableResId(photo.resourceName)
            if (resId != 0) {
                // Call Gemini analyzer
                val result = GeminiAnalyzer.analyzePhoto(context, resId, photo.resourceName, photo.title)
                
                // Update photo in database with results
                val updatedPhoto = photo.copy(
                    faceTag = result.personName ?: "No Face", // tag with name or "No Face" if none detected
                    title = if (photo.title.contains("Portrait") || photo.title.contains("Headshot")) {
                        result.personName?.let { "$it's AI Portrait" } ?: photo.title
                    } else photo.title,
                    attire = result.attire,
                    environment = result.environment,
                    lighting = result.lighting
                )
                photoDao.updatePhoto(updatedPhoto)
            }
        }
    }

    // Insert a new dynamic photo/video with duplication prevention check
    suspend fun importPhoto(
        resourceName: String,
        title: String,
        sizeBytes: Long,
        isFaceOf: String? = null, // optional manual face tag
        mimeType: String = "image/jpeg",
        durationSeconds: Int = 0,
        attire: String? = null,
        environment: String? = null,
        lighting: String? = null
    ): ImportResult = withContext(Dispatchers.IO) {
        val hash = computeMockHash(resourceName + "_" + mimeType + "_" + durationSeconds + "_" + title)
        
        // 1. Check for duplicates
        val existing = photoDao.getPhotoByHash(hash)
        if (existing != null) {
            Log.w(TAG, "Import blocked: duplicate photo detected with hash $hash")
            return@withContext ImportResult.Duplicate(existing)
        }

        // 2. Insert new photo or video
        val newPhoto = PhotoEntity(
            resourceName = resourceName,
            title = title,
            sizeBytes = sizeBytes,
            dateTaken = System.currentTimeMillis(),
            originalContentHash = hash,
            faceTag = isFaceOf,
            mimeType = mimeType,
            durationSeconds = durationSeconds,
            attire = attire,
            environment = environment,
            lighting = lighting
        )
        val id = photoDao.insertPhoto(newPhoto)
        val inserted = photoDao.getPhotoById(id) ?: newPhoto.copy(id = id)
        
        ImportResult.Success(inserted)
    }

    // Apply edit (non-destructive - saves filter state & adds version history)
    suspend fun applyFilter(photo: PhotoEntity, filterName: String) = withContext(Dispatchers.IO) {
        // 1. Get current versions to find next version number
        val currentVersions = photoDao.getVersionsForPhotoSync(photo.id)
        val nextVersionNum = currentVersions.size + 1

        // 2. Insert version history record (takes 0 physical storage space!)
        val version = PhotoEditVersionEntity(
            photoId = photo.id,
            versionNumber = nextVersionNum,
            filterName = filterName,
            timestamp = System.currentTimeMillis(),
            sizeDifferenceBytes = 0L // Non-destructive edit metadata is virtually size-free!
        )
        photoDao.insertVersion(version)

        // 3. Update main photo with active filter
        val updatedPhoto = photo.copy(
            currentFilter = filterName
        )
        photoDao.updatePhoto(updatedPhoto)
    }

    // Revert to a specific edit version
    suspend fun revertToVersion(photo: PhotoEntity, version: PhotoEditVersionEntity) = withContext(Dispatchers.IO) {
        // 1. Find all versions above this version number and delete them
        val allVersions = photoDao.getVersionsForPhotoSync(photo.id)
        allVersions.filter { it.versionNumber > version.versionNumber }.forEach {
            photoDao.deleteVersion(photo.id, it.versionNumber)
        }

        // 2. Update main photo's filter to this version's filter
        val updatedPhoto = photo.copy(
            currentFilter = version.filterName
        )
        photoDao.updatePhoto(updatedPhoto)
    }

    // Revert completely to original state
    suspend fun revertToOriginal(photo: PhotoEntity) = withContext(Dispatchers.IO) {
        // 1. Clear version history for this photo
        photoDao.deleteVersionsForPhoto(photo.id)

        // 2. Reset filter state to original (null)
        val updatedPhoto = photo.copy(
            currentFilter = null
        )
        photoDao.updatePhoto(updatedPhoto)
    }

    // Delete a photo from database
    suspend fun deletePhoto(photo: PhotoEntity) = withContext(Dispatchers.IO) {
        photoDao.deleteVersionsForPhoto(photo.id)
        photoDao.deletePhoto(photo)
    }

    // Run Cloud Backup simulation
    suspend fun syncBackup(
        cloudAppName: String,
        onProgress: (Int, Int) -> Unit
    ) = withContext(Dispatchers.IO) {
        val photos = allPhotos.first()
        val unsynced = photos.filter { !it.hasBackupSynced }
        val total = unsynced.size
        
        unsynced.forEachIndexed { index, photo ->
            onProgress(index + 1, total)
            // Artificial delay to simulate backup upload
            kotlinx.coroutines.delay(800)
            
            val updated = photo.copy(
                hasBackupSynced = true,
                backupLocation = cloudAppName
            )
            photoDao.updatePhoto(updated)
        }
    }

    // Helper to get Resource ID by Name
    fun getDrawableResId(resName: String): Int {
        return when (resName) {
            "img_face_alice" -> R.drawable.img_face_alice
            "img_face_bob" -> R.drawable.img_face_bob
            "img_face_clara" -> R.drawable.img_face_clara
            "img_scenic_mountain" -> R.drawable.img_scenic_mountain
            "img_scenic_lake" -> R.drawable.img_scenic_lake
            else -> 0
        }
    }

    private fun computeMockHash(resName: String): String {
        // Return a mock MD5-like string representing the content hash
        return "md5_${resName.hashCode().toString(16)}"
    }
}

sealed class ImportResult {
    data class Success(val photo: PhotoEntity) : ImportResult()
    data class Duplicate(val existingPhoto: PhotoEntity) : ImportResult()
}

package ai.sakana.aura.gallery.data.cloud

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudSyncEngineTest {
    private val photoA = ByteArray(300) { (it % 5).toByte() }
    private val photoB = ByteArray(400) { (it % 7).toByte() }
    private val sleeps = mutableListOf<Long>()
    private fun retry() = RetryPolicy(sleeper = { sleeps += it })
    private fun engine(api: FakeCloudApi, gateway: FakeGateway) = CloudSyncEngine(api, gateway, { 9_000L }, retry())
    private fun manifestOf(api: FakeCloudApi) = BackupManifestJson.fromJson(api.manifest!!)

    @Test fun firstSyncUploadsEverythingAndWritesManifest() = runBlocking {
        val api = FakeCloudApi(); val gw = FakeGateway()
        gw.localFiles["u/1"] = photoA; gw.localFiles["u/2"] = photoB
        val report = engine(api, gw).sync(listOf(snapshot("u/1", "a.jpg", photoA), snapshot("u/2", "b.jpg", photoB)))
        assertEquals(2, report.uploaded)
        assertEquals(setOf("u/1", "u/2"), report.syncedKeys)
        val m = manifestOf(api)
        assertEquals(2, m.entries.size)
        assertEquals(sha256Of(photoA), m.entries.first { it.contentUri == "u/1" }.sha256Hash)
    }

    @Test fun secondSyncUploadsNothingAndNeverReadsFiles() = runBlocking {
        val api = FakeCloudApi(); val gw = FakeGateway()
        gw.localFiles["u/1"] = photoA
        val locals = listOf(snapshot("u/1", "a.jpg", photoA))
        engine(api, gw).sync(locals)
        val calls = api.uploadCalls
        gw.localFiles.clear() // proves unchanged items are skipped by size/mtime without opening the file
        val report = engine(api, gw).sync(locals)
        assertEquals(0, report.uploaded)
        assertEquals(1, report.unchanged)
        assertEquals(calls, api.uploadCalls)
    }

    @Test fun modifiedFileIsReuploadedOverTheSameRemoteFile() = runBlocking {
        val api = FakeCloudApi(); val gw = FakeGateway()
        gw.localFiles["u/1"] = photoA
        engine(api, gw).sync(listOf(snapshot("u/1", "a.jpg", photoA)))
        val remoteId = manifestOf(api).entries.single().remoteId
        val edited = ByteArray(310) { 7 }
        gw.localFiles["u/1"] = edited
        val report = engine(api, gw).sync(listOf(snapshot("u/1", "a.jpg", edited, modified = 500L)))
        assertEquals(1, report.uploaded)
        assertEquals(1, api.files.size)
        assertTrue(api.files[remoteId]!!.contentEquals(edited))
    }

    @Test fun metadataOnlyChangeDoesNotReupload() = runBlocking {
        val api = FakeCloudApi(); val gw = FakeGateway()
        gw.localFiles["u/1"] = photoA
        engine(api, gw).sync(listOf(snapshot("u/1", "a.jpg", photoA)))
        val calls = api.uploadCalls
        val report = engine(api, gw).sync(listOf(snapshot("u/1", "a.jpg", photoA, modified = 900L, favorite = true)))
        assertEquals(calls, api.uploadCalls)
        assertEquals(1, report.metadataUpdated)
        assertTrue(manifestOf(api).entries.single().isFavorite)
    }

    @Test fun identicalLocalFilesUploadOnlyOnce() = runBlocking {
        val api = FakeCloudApi(); val gw = FakeGateway()
        gw.localFiles["u/1"] = photoA; gw.localFiles["u/2"] = photoA.copyOf()
        val report = engine(api, gw).sync(listOf(snapshot("u/1", "a.jpg", photoA), snapshot("u/2", "copy.jpg", photoA)))
        assertEquals(1, report.uploaded)
        assertEquals(1, api.files.size)
    }

    @Test fun rateLimitRetriesWithOneTwoFourSecondBackoff() = runBlocking {
        val api = FakeCloudApi().apply { rateLimitUploads = 3 }; val gw = FakeGateway()
        gw.localFiles["u/1"] = photoA
        val report = engine(api, gw).sync(listOf(snapshot("u/1", "a.jpg", photoA)))
        assertEquals(1, report.uploaded)
        assertEquals(listOf(1_000L, 2_000L, 4_000L), sleeps)
    }

    @Test fun persistentRateLimitBecomesAReportedFailure() = runBlocking {
        val api = FakeCloudApi().apply { rateLimitUploads = 99 }; val gw = FakeGateway()
        gw.localFiles["u/1"] = photoA
        val report = engine(api, gw).sync(listOf(snapshot("u/1", "a.jpg", photoA)))
        assertEquals(0, report.uploaded)
        assertEquals(1, report.failures.size)
        assertEquals(4, api.uploadCalls) // first try + 3 retries
    }

    @Test fun manifestIsCheckpointedPerBatch() = runBlocking {
        val api = FakeCloudApi(); val gw = FakeGateway()
        val locals = (1..25).map { i -> val b = ByteArray(10 + i) { i.toByte() }; gw.localFiles["u/$i"] = b; snapshot("u/$i", "p$i.jpg", b) }
        engine(api, gw).sync(locals, SyncOptions(batchSize = 10))
        assertEquals(3, api.manifestWrites)
        assertEquals(25, manifestOf(api).entries.size)
    }

    @Test fun twoWaySyncDownloadsNewCloudItemsWithIntegrityCheck() = runBlocking {
        val api = FakeCloudApi(); val gw = FakeGateway()
        api.files["r1"] = photoB; api.names["r1"] = "b.jpg"
        api.manifest = BackupManifestJson.toJson(BackupManifest(entries = listOf(entry("other/9", "r1", "b.jpg", photoB))))
        val report = engine(api, gw).sync(emptyList())
        assertEquals(1, report.downloaded)
        assertTrue(gw.localFiles.values.single().contentEquals(photoB))
    }

    @Test fun corruptedDownloadIsDeletedAndReported() = runBlocking {
        val api = FakeCloudApi().apply { corruptDownloads = true }; val gw = FakeGateway()
        api.files["r1"] = photoB
        api.manifest = BackupManifestJson.toJson(BackupManifest(entries = listOf(entry("other/9", "r1", "b.jpg", photoB))))
        val report = engine(api, gw).sync(emptyList())
        assertEquals(0, report.downloaded)
        assertTrue(report.failures.single().message.contains("Integrity"))
        assertTrue(gw.localFiles.isEmpty())
    }

    @Test fun lowStorageSkipsVideoDownloadsOnly() = runBlocking {
        val api = FakeCloudApi(); val gw = FakeGateway()
        val clip = ByteArray(500) { 5 }
        api.files["r1"] = photoB; api.files["r2"] = clip
        api.manifest = BackupManifestJson.toJson(BackupManifest(entries = listOf(
            entry("o/1", "r1", "b.jpg", photoB), entry("o/2", "r2", "c.mp4", clip, mediaType = "VIDEO"),
        )))
        val report = engine(api, gw).sync(emptyList(), SyncOptions(skipVideoDownloads = true))
        assertEquals(1, report.downloaded)
        assertEquals(1, report.skippedVideos)
    }

    @Test fun hashLookupAvoidsUploadingKnownContent() = runBlocking {
        val api = FakeCloudApi(supportsHashLookup = true); val gw = FakeGateway()
        api.files["existing"] = photoA
        gw.localFiles["u/1"] = photoA
        val report = engine(api, gw).sync(listOf(snapshot("u/1", "a.jpg", photoA)))
        assertEquals(0, api.uploadCalls)
        assertEquals(1, report.dedupedByHash)
        assertEquals("existing", manifestOf(api).entries.single().remoteId)
    }

    // ---------------------------------------------------------------- conflicts

    private fun nameClash(api: FakeCloudApi, gw: FakeGateway): List<LocalMediaSnapshot> {
        val cloudVersion = ByteArray(350) { 9 }
        api.files["r1"] = cloudVersion
        api.manifest = BackupManifestJson.toJson(BackupManifest(entries = listOf(entry("old/1", "r1", "a.jpg", cloudVersion))))
        gw.localFiles["u/1"] = photoA
        return listOf(snapshot("u/1", "a.jpg", photoA))
    }

    @Test fun sameNameDifferentHashKeepDeviceUploadsOverCloud() = runBlocking {
        val api = FakeCloudApi(); val gw = FakeGateway()
        val report = engine(api, gw).sync(nameClash(api, gw), SyncOptions(conflictMode = ConflictMode.KEEP_DEVICE))
        assertEquals(ConflictType.SAME_NAME_DIFFERENT_HASH, report.conflicts.single().conflict.type)
        assertTrue(api.files["r1"]!!.contentEquals(photoA))
        assertTrue(gw.trashed.isEmpty())
    }

    @Test fun sameNameDifferentHashKeepCloudSavesCloudCopyBeforeTrashingLocal() = runBlocking {
        val api = FakeCloudApi(); val gw = FakeGateway()
        val report = engine(api, gw).sync(nameClash(api, gw), SyncOptions(conflictMode = ConflictMode.KEEP_CLOUD))
        assertEquals(1, report.downloaded)
        assertEquals(listOf("u/1"), gw.trashed)
        assertEquals(0, api.uploadCalls)
    }

    @Test fun sameNameDifferentHashKeepBothRenamesCloudCopyWithSuffix() = runBlocking {
        val api = FakeCloudApi(); val gw = FakeGateway()
        engine(api, gw).sync(nameClash(api, gw), SyncOptions(conflictMode = ConflictMode.KEEP_BOTH))
        assertEquals("a_cloud.jpg", api.names["r1"])
        assertEquals(1, api.uploadCalls)
        assertEquals(2, manifestOf(api).entries.size)
    }

    @Test fun askModeDefersWhenNoResolverAndAppliesChoiceWhenGiven() = runBlocking {
        val api = FakeCloudApi(); val gw = FakeGateway()
        val locals = nameClash(api, gw)
        val deferred = engine(api, gw).sync(locals, SyncOptions(conflictMode = ConflictMode.ASK))
        assertEquals(1, deferred.pendingConflicts.size)
        assertEquals(0, api.uploadCalls)
        val resolved = engine(api, gw).sync(locals, SyncOptions(conflictMode = ConflictMode.ASK, askResolver = { ConflictMode.KEEP_DEVICE }))
        assertTrue(resolved.pendingConflicts.isEmpty())
        assertEquals(1, api.uploadCalls)
    }

    @Test fun resolveConflictAppliesALaterChoice() = runBlocking {
        val api = FakeCloudApi(); val gw = FakeGateway()
        val deferred = engine(api, gw).sync(nameClash(api, gw), SyncOptions(conflictMode = ConflictMode.ASK))
        engine(api, gw).resolveConflict(deferred.pendingConflicts.single(), ConflictMode.KEEP_DEVICE)
        assertTrue(api.files["r1"]!!.contentEquals(photoA))
        assertThrows(IllegalArgumentException::class.java) { runBlocking { engine(api, gw).resolveConflict(deferred.pendingConflicts.single(), ConflictMode.ASK) } }
        Unit
    }

    private fun sameHashSetup(api: FakeCloudApi, gw: FakeGateway): List<LocalMediaSnapshot> {
        api.files["r1"] = photoA
        api.manifest = BackupManifestJson.toJson(BackupManifest(entries = listOf(entry("old/1", "r1", "cloud-name.jpg", photoA))))
        gw.localFiles["u/1"] = photoA
        return listOf(snapshot("u/1", "device-name.jpg", photoA))
    }

    @Test fun sameHashDifferentNameKeepDeviceRenamesCloudCopy() = runBlocking {
        val api = FakeCloudApi(); val gw = FakeGateway()
        val report = engine(api, gw).sync(sameHashSetup(api, gw), SyncOptions(conflictMode = ConflictMode.KEEP_DEVICE))
        assertEquals(ConflictType.SAME_HASH_DIFFERENT_NAME, report.conflicts.single().conflict.type)
        assertEquals("device-name.jpg", api.names["r1"])
        assertEquals(0, api.uploadCalls)
    }

    @Test fun sameHashDifferentNameKeepCloudRenamesLocalCopy() = runBlocking {
        val api = FakeCloudApi(); val gw = FakeGateway()
        engine(api, gw).sync(sameHashSetup(api, gw), SyncOptions(conflictMode = ConflictMode.KEEP_CLOUD))
        assertEquals(listOf("u/1" to "cloud-name.jpg"), gw.renamedLocal)
    }

    @Test fun deletedLocallyKeepDeviceLeavesCloudAloneAndKeepCloudRestores() = runBlocking {
        val api = FakeCloudApi(); val gw = FakeGateway()
        api.files["r1"] = photoA
        api.manifest = BackupManifestJson.toJson(BackupManifest(entries = listOf(entry("u/1", "r1", "a.jpg", photoA))))
        val known = setOf("u/1")
        val keep = engine(api, gw).sync(emptyList(), SyncOptions(conflictMode = ConflictMode.KEEP_DEVICE, previouslySyncedKeys = known))
        assertEquals(ConflictType.DELETED_LOCALLY_EXISTS_IN_CLOUD, keep.conflicts.single().conflict.type)
        assertEquals(0, keep.downloaded)
        assertTrue(api.files.containsKey("r1"))
        val restore = engine(api, gw).sync(emptyList(), SyncOptions(conflictMode = ConflictMode.KEEP_CLOUD, previouslySyncedKeys = known))
        assertEquals(1, restore.downloaded)
    }

    @Test fun deletedInCloudKeepDeviceReuploadsAndKeepCloudTrashesLocal() = runBlocking {
        val api = FakeCloudApi(); val gw = FakeGateway()
        gw.localFiles["u/1"] = photoA
        val locals = listOf(snapshot("u/1", "a.jpg", photoA))
        val known = setOf("u/1")
        val device = engine(api, gw).sync(locals, SyncOptions(conflictMode = ConflictMode.KEEP_DEVICE, previouslySyncedKeys = known))
        assertEquals(ConflictType.DELETED_IN_CLOUD_EXISTS_LOCALLY, device.conflicts.single().conflict.type)
        assertEquals(1, device.uploaded)
        val api2 = FakeCloudApi()
        val cloud = engine(api2, gw).sync(locals, SyncOptions(conflictMode = ConflictMode.KEEP_CLOUD, previouslySyncedKeys = known))
        assertEquals(listOf("u/1"), gw.trashed)
        assertEquals(0, api2.uploadCalls)
        assertFalse(cloud.syncedKeys.contains("u/1"))
    }

    @Test fun conflictDecisionTableNeverDeletesPermanently() {
        for (type in ConflictType.values()) for (mode in ConflictMode.values()) {
            assertNotNull(CloudConflictResolver.plan(type, mode))
        }
        assertEquals(ConflictAction.DEFER, CloudConflictResolver.plan(ConflictType.SAME_NAME_DIFFERENT_HASH, ConflictMode.ASK))
        assertEquals("a_cloud.jpg", CloudConflictResolver.cloudCopyName("a.jpg"))
        assertEquals("noext_cloud", CloudConflictResolver.cloudCopyName("noext"))
        assertEquals(ConflictMode.KEEP_DEVICE, CloudConflictResolver.DEFAULT_MODE)
    }

    // ------------------------------------------------------------------ import

    @Test fun importDownloadsOnlyMissingItemsInScope() = runBlocking {
        val api = FakeCloudApi(); val gw = FakeGateway()
        val fav = ByteArray(120) { 3 }
        api.files["r1"] = photoA; api.files["r2"] = photoB; api.files["r3"] = fav
        api.manifest = BackupManifestJson.toJson(BackupManifest(entries = listOf(
            entry("o/1", "r1", "a.jpg", photoA), entry("o/2", "r2", "b.jpg", photoB), entry("o/3", "r3", "f.jpg", fav, favorite = true),
        )))
        val present = listOf(snapshot("u/1", "a.jpg", photoA))
        val all = engine(api, gw).importFromCloud(ImportOptions(), present)
        assertEquals(2, all.downloaded)
        assertEquals(1, all.alreadyPresent)
        val gw2 = FakeGateway()
        val favorites = engine(api, gw2).importFromCloud(ImportOptions(scope = ImportScope.FAVORITES_ONLY), emptyList())
        assertEquals(1, favorites.downloaded)
        assertEquals(listOf("f.jpg"), gw2.saved)
    }

    @Test fun backupsListShowsCounts() = runBlocking {
        val api = FakeCloudApi(); val gw = FakeGateway()
        api.manifest = BackupManifestJson.toJson(BackupManifest(updatedAtEpochMillis = 77L, entries = listOf(
            entry("o/1", "r1", "a.jpg", photoA, favorite = true), entry("o/2", "r2", "c.mp4", photoB, mediaType = "VIDEO"),
        )))
        val info = engine(api, gw).listBackups().single()
        assertEquals(2, info.itemCount); assertEquals(1, info.photoCount); assertEquals(1, info.videoCount); assertEquals(1, info.favoriteCount)
        assertEquals(700L, info.totalBytes); assertEquals(77L, info.updatedAtEpochMillis)
        assertTrue(engine(FakeCloudApi(), gw).listBackups().isEmpty())
    }
}

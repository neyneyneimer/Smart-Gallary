package ai.sakana.aura.gallery.data.cloud

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudManagersTest {
    private val photo = ByteArray(200) { (it % 11).toByte() }
    private fun locals(gw: FakeGateway): List<LocalMediaSnapshot> { gw.localFiles["u/1"] = photo; return listOf(snapshot("u/1", "a.jpg", photo)) }

    // ---- shared behaviour exercised through each provider manager (Tasks 5, 6, 7 test files) ----

    @Test fun googleDriveManagerSyncsAndPersistsState() = runBlocking {
        val store = InMemoryCloudSyncStateStore(); val gw = FakeGateway()
        val drive = GoogleDriveBackupManager(FakeCloudApi(), gw, store, { 5L })
        assertEquals("Google Drive", drive.getProviderName())
        val report = drive.sync(locals(gw))
        assertEquals(1, report.uploaded)
        val status = drive.getStatus()
        assertEquals(5L, status.lastSyncEpochMillis); assertEquals(1, status.syncedCount); assertNull(status.lastError)
        assertEquals(1, drive.getBackups().single().itemCount)
    }

    @Test fun syncRequiresConnection() {
        val drive = GoogleDriveBackupManager(FakeCloudApi(connected = false), FakeGateway(), InMemoryCloudSyncStateStore())
        assertThrows(CloudAuthException::class.java) { runBlocking { drive.sync(emptyList()) } }
    }

    @Test fun failedSyncIsRecordedInStatus() = runBlocking {
        val store = InMemoryCloudSyncStateStore()
        val api = object : CloudApi by FakeCloudApi() {
            override suspend fun readManifest(): String? = throw CloudException("boom")
        }
        val drive = GoogleDriveBackupManager(api, FakeGateway(), store)
        assertThrows(CloudException::class.java) { runBlocking { drive.sync(emptyList()) } }
        assertEquals("boom", drive.getStatus().lastError)
    }

    @Test fun deletionIsDetectedOnTheNextSyncUsingStoredState() = runBlocking {
        val store = InMemoryCloudSyncStateStore(); val gw = FakeGateway(); val api = FakeCloudApi()
        val drive = GoogleDriveBackupManager(api, gw, store)
        drive.sync(locals(gw))
        val second = drive.sync(emptyList())
        assertEquals(ConflictType.DELETED_LOCALLY_EXISTS_IN_CLOUD, second.conflicts.single().conflict.type)
        assertEquals(1, api.files.size) // default mode never deletes the cloud copy
    }

    @Test fun megaRequiresCredentialsForFirstSignInButNotWhenSessionExists() = runBlocking {
        val gw = FakeGateway(); val store = InMemoryCloudSyncStateStore()
        val signedOut = MegaBackupManager(FakeCloudApi(connected = false), gw, store)
        assertTrue(signedOut.connect(null).isFailure)
        assertTrue(signedOut.connect(CloudCredentials("a@b.c", "")).isFailure)
        assertTrue(signedOut.connect(CloudCredentials("a@b.c", "secret")).isSuccess)
        assertTrue(MegaBackupManager(FakeCloudApi(connected = true), gw, store).connect(null).isSuccess)
        assertEquals("MEGA", signedOut.getProviderName())
    }

    @Test fun megaVerifiesIntegrityOnDownload() = runBlocking {
        val api = FakeCloudApi().apply { corruptDownloads = true }; val gw = FakeGateway()
        api.files["r1"] = photo
        api.manifest = BackupManifestJson.toJson(BackupManifest(entries = listOf(entry("o/1", "r1", "a.jpg", photo))))
        val report = MegaBackupManager(api, gw, InMemoryCloudSyncStateStore()).importFromCloud()
        assertEquals(0, report.downloaded)
        assertTrue(report.failures.single().message.contains("Integrity"))
    }

    @Test fun teraboxSkipsUploadWhenCloudAlreadyHasTheHash() = runBlocking {
        val api = FakeCloudApi(supportsHashLookup = true); val gw = FakeGateway()
        api.files["known"] = photo
        val report = TeraboxBackupManager(api, gw, InMemoryCloudSyncStateStore()).sync(locals(gw))
        assertEquals(1, report.dedupedByHash); assertEquals(0, api.uploadCalls)
    }

    // ---- TeraBox token refresh ----

    private class TokenStore(var tokens: TeraboxTokens?) : TeraboxTokenStore {
        override suspend fun load() = tokens
        override suspend fun save(tokens: TeraboxTokens) { this.tokens = tokens }
    }

    @Test fun validTokenIsReturnedWithoutRefresh() = runBlocking {
        val store = TokenStore(TeraboxTokens("A", "R", 10_000_000L))
        val manager = TeraboxTokenManager(store, { error("must not refresh") }, { 1_000L })
        assertEquals("A", manager.validAccessToken())
    }

    @Test fun tokenNearExpiryIsRefreshedAndSaved() = runBlocking {
        val store = TokenStore(TeraboxTokens("A", "R", 1_000L + 4 * 60 * 1000L))
        val manager = TeraboxTokenManager(store, { TeraboxTokens("B", "R2", 99_999_999L) }, { 1_000L })
        assertEquals("B", manager.validAccessToken())
        assertEquals("R2", store.tokens!!.refreshToken)
    }

    @Test fun failedRefreshAsksForSignIn() {
        val manager = TeraboxTokenManager(TokenStore(TeraboxTokens("A", "R", 0L)), { throw java.io.IOException("offline") }, { 1_000L })
        assertThrows(CloudAuthException::class.java) { runBlocking { manager.validAccessToken() } }
        assertThrows(CloudAuthException::class.java) { runBlocking { TeraboxTokenManager(TokenStore(null), { error("x") }).validAccessToken() } }
    }

    // ---- factory ----

    @Test fun factoryOnlyOffersRegisteredProviders() {
        val drive = GoogleDriveBackupManager(FakeCloudApi(), FakeGateway(), InMemoryCloudSyncStateStore())
        val factory = CloudProviderFactory(mapOf(CloudProviderType.GOOGLE_DRIVE to drive))
        assertNotNull(factory.create(CloudProviderType.GOOGLE_DRIVE))
        assertNull(factory.create(CloudProviderType.MEGA))
        assertNull(factory.create(CloudProviderType.NONE))
        assertEquals(listOf(true, false, false), factory.descriptors().map { it.isAvailable })
        assertFalse(factory.isAvailable(CloudProviderType.TERABOX))
    }

    // ---- manifest + JSON ----

    @Test fun manifestRoundTripsIncludingAwkwardNames() {
        val e = entry("content://m/1", "r1", "Caf\u00e9 \"best\" \\ day.jpg", photo, favorite = true)
        val back = BackupManifestJson.fromJson(BackupManifestJson.toJson(BackupManifest(2, 123L, listOf(e))))
        assertEquals(listOf(e), back.entries)
        assertEquals(123L, back.updatedAtEpochMillis)
    }

    @Test fun malformedManifestIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { BackupManifestJson.fromJson("{not json") }
        assertThrows(IllegalArgumentException::class.java) { BackupManifestJson.fromJson("[1,2]") }
        assertThrows(IllegalArgumentException::class.java) { BackupManifestJson.fromJson("{\"entries\":[{\"remoteId\":\"x\"}]}") }
    }

    @Test fun miniJsonHandlesNestedValuesAndEscapes() {
        val parsed = MiniJson.parse("{\"a\":[1,2.5,true,null,\"x\\n\\u0041\"],\"b\":{\"c\":-3}}") as Map<*, *>
        assertEquals(listOf(1L, 2.5, true, null, "x\nA"), parsed["a"])
        assertEquals(-3L, (parsed["b"] as Map<*, *>)["c"])
        assertEquals("{\"k\":[1,\"a\\\"b\"]}", MiniJson.write(mapOf("k" to listOf(1, "a\"b"))))
    }

    // ---- backup policy ----

    @Test fun retryBackoffIsFifteenThirtySixtyThenStops() {
        assertEquals(15L, BackupPolicy.retryDelayMinutes(1)); assertEquals(30L, BackupPolicy.retryDelayMinutes(2))
        assertEquals(60L, BackupPolicy.retryDelayMinutes(3)); assertNull(BackupPolicy.retryDelayMinutes(4)); assertNull(BackupPolicy.retryDelayMinutes(0))
    }

    @Test fun storageGuardUsesFiveHundredMegabytes() {
        assertTrue(BackupPolicy.shouldSkipVideoDownloads(499L * 1024 * 1024))
        assertFalse(BackupPolicy.shouldSkipVideoDownloads(500L * 1024 * 1024))
    }

    @Test fun dailyRunTargetsTwoAm() {
        val zone = java.time.ZoneId.of("UTC")
        val at = { h: Int, m: Int -> java.time.ZonedDateTime.of(2026, 11, 20, h, m, 0, 0, zone) }
        assertEquals(4 * 3_600_000L, BackupPolicy.delayUntilNextDailyRunMillis(at(22, 0)))
        assertEquals(3_600_000L, BackupPolicy.delayUntilNextDailyRunMillis(at(1, 0)))
        assertEquals(24 * 3_600_000L, BackupPolicy.delayUntilNextDailyRunMillis(at(2, 0)))
        assertEquals(BackupPolicy.Network.UNMETERED_ONLY, BackupPolicy.network(true))
    }
}

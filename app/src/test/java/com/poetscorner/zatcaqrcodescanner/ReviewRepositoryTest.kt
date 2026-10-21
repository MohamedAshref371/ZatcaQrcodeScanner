package com.poetscorner.zatcaqrcodescanner

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.PreferencesSerializer
import androidx.datastore.core.okio.OkioStorage
import com.poetscorner.zatcaqrcodescanner.data.ReviewRepository
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import okio.Path.Companion.toPath
import okio.FileSystem

class ReviewRepositoryTest {
    @Test fun persistsUsageAndAllowsExactlyOneConcurrentClaim() = runBlocking {
        val directory = Files.createTempDirectory("review-test").toFile()
        val file = directory.resolve("usage.preferences_pb")
        var job = SupervisorJob()
        // Okio uses atomic replacement on Windows; java.io.File.renameTo does not.
        fun store() = PreferenceDataStoreFactory.create(
            storage = OkioStorage(FileSystem.SYSTEM, PreferencesSerializer,
                producePath = { file.absolutePath.toPath() }),
            scope = CoroutineScope(Dispatchers.IO + job))
        try {
            var repository = ReviewRepository(store())
            val start = 1700000000000L
            val day = 86400000L
            repeat(3) { repository.recordUse(start + it * day) }
            repeat(30) { repository.recordSavedInvoice("same", start) }
            assertFalse(repository.claimAttempt(start + 7 * day))
            repeat(9) { repository.recordSavedInvoice("invoice-$it", start) }
            assertFalse(repository.claimAttempt(start + 7 * day - 1))

            // Reopen the persisted file, as after a process restart.
            job.cancelAndJoin()
            job = SupervisorJob()
            repository = ReviewRepository(store())
            val claims = (1..12).map { async(Dispatchers.Default) {
                repository.claimAttempt(start + 7 * day)
            } }.awaitAll()
            assertEquals(1, claims.count { it })

            job.cancelAndJoin()
            job = SupervisorJob()
            repository = ReviewRepository(store())
            repository.recordUse(start + 365 * day)
            assertFalse(repository.claimAttempt(start + 365 * day))
        } finally {
            job.cancelAndJoin()
            directory.deleteRecursively()
        }
    }
}

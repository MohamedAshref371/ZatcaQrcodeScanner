package com.poetscorner.invoiceqr

import com.poetscorner.invoiceqr.data.ScanEngine
import com.poetscorner.invoiceqr.scanner.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class MlKitModuleControllerTest {
    private class Installer : MlKitModuleInstaller {
        @Volatile var installed = false
        @Volatile var unavailable = false
        var makesAvailable = true
        var installs = 0
        @Volatile var checks = 0
        var closed = false
        var result = CompletableDeferred<Unit>()
        lateinit var report: (MlKitModuleState) -> Unit
        override suspend fun available(): Boolean {
            checks++
            if (unavailable) throw MlKitServicesUnavailable()
            return installed
        }
        override suspend fun install(onProgress: (MlKitModuleState) -> Unit) {
            installs++
            report = onProgress
            result.await()
            installed = makesAvailable
        }
        override fun close() { closed = true }
    }

    private suspend fun awaitCondition(condition: () -> Boolean) {
        withTimeout(5_000) { while (!condition()) delay(10) }
    }

    @Test fun requiresActualCompletionAndKeepsZxingAvailableDuringDownload() = runBlocking<Unit> {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val installer = Installer()
        val controller = MlKitModuleController(installer, scope)
        try {
            controller.refresh()
            awaitCondition { controller.state.value.status == MlKitModuleStatus.NOT_INSTALLED }
            assertEquals(MlKitModuleStatus.NOT_INSTALLED, controller.state.value.status)
            controller.download()
            controller.download()
            controller.refresh()
            awaitCondition { installer.installs == 1 }
            assertEquals(1, installer.installs)
            assertFalse(controller.state.value.ready)
            installer.report(MlKitModuleState(MlKitModuleStatus.DOWNLOADING, 0.5f))
            assertEquals(0.5f, controller.state.value.progress)
            assertEquals(ScanEngine.ZXING, controller.state.value.effectiveEngine(ScanEngine.ML_KIT))
            installer.report(MlKitModuleState(MlKitModuleStatus.PAUSED, 0.5f))
            assertTrue(controller.state.value.inProgress)
            installer.report(MlKitModuleState(MlKitModuleStatus.INSTALLING, 1f))
            assertFalse(controller.state.value.ready)
            installer.result.complete(Unit)
            withTimeout(5000) { controller.state.first { it.ready } }
            assertEquals(ScanEngine.ML_KIT, controller.state.value.effectiveEngine(ScanEngine.ML_KIT))
            assertEquals(ScanEngine.ZXING, controller.state.value.effectiveEngine(ScanEngine.ZXING))
        } finally { controller.close(); scope.cancel() }
    }

    @Test fun failedDownloadCanBeRetriedAndRefreshDoesNotEraseTheError() = runBlocking<Unit> {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val installer = Installer()
        val controller = MlKitModuleController(installer, scope)
        try {
            controller.download()
            awaitCondition { installer.installs == 1 }
            installer.result.completeExceptionally(java.io.IOException("offline"))
            awaitCondition { controller.state.value.status == MlKitModuleStatus.FAILED }
            assertEquals(MlKitModuleStatus.FAILED, controller.state.value.status)
            controller.refresh()
            awaitCondition { installer.checks >= 2 && controller.state.value.status == MlKitModuleStatus.FAILED }
            assertEquals(MlKitModuleStatus.FAILED, controller.state.value.status)
            installer.result = CompletableDeferred()
            controller.download()
            awaitCondition { installer.installs == 2 }
            assertEquals(2, installer.installs)
            installer.result.complete(Unit)
            withTimeout(5000) { controller.state.first { it.ready } }
        } finally { controller.close(); scope.cancel() }
    }

    @Test fun rechecksInstalledModulesAndFallsBackWhenTheyDisappear() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val installer = Installer().apply { installed = true }
        val controller = MlKitModuleController(installer, scope)
        try {
            controller.refresh()
            awaitCondition { controller.state.value.ready }
            assertTrue(controller.state.value.ready)
            controller.download()
            assertEquals(0, installer.installs)
            installer.installed = false
            controller.refresh()
            awaitCondition { controller.state.value.status == MlKitModuleStatus.NOT_INSTALLED }
            assertEquals(ScanEngine.ZXING, controller.state.value.effectiveEngine(ScanEngine.ML_KIT))
            installer.unavailable = true
            controller.refresh()
            awaitCondition { controller.state.value.status == MlKitModuleStatus.UNAVAILABLE }
            assertEquals(MlKitModuleStatus.UNAVAILABLE, controller.state.value.status)
        } finally { controller.close(); scope.cancel() }
        assertTrue(installer.closed)
    }

    @Test fun neverMarksReadyIfFinalAvailabilityCheckFails() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val installer = Installer().apply { makesAvailable = false; result.complete(Unit) }
        val controller = MlKitModuleController(installer, scope)
        try {
            controller.download()
            awaitCondition { controller.state.value.status == MlKitModuleStatus.FAILED }
            assertEquals(MlKitModuleStatus.FAILED, controller.state.value.status)
            assertFalse(controller.state.value.ready)
        } finally { controller.close(); scope.cancel() }
    }
}

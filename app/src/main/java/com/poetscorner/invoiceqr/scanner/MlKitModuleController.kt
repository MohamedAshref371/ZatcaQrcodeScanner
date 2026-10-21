package com.poetscorner.invoiceqr.scanner

import com.poetscorner.invoiceqr.data.ScanEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class MlKitModuleStatus { CHECKING, NOT_INSTALLED, DOWNLOADING, PAUSED, INSTALLING, READY, FAILED, UNAVAILABLE }

data class MlKitModuleState(val status: MlKitModuleStatus = MlKitModuleStatus.CHECKING,
    val progress: Float? = null) {
    val ready get() = status == MlKitModuleStatus.READY
    val inProgress get() = status in setOf(MlKitModuleStatus.DOWNLOADING, MlKitModuleStatus.PAUSED, MlKitModuleStatus.INSTALLING)
    fun effectiveEngine(preferred: ScanEngine) = if (preferred == ScanEngine.ML_KIT && !ready) ScanEngine.ZXING else preferred
}

internal class MlKitServicesUnavailable : Exception()

internal interface MlKitModuleInstaller {
    suspend fun available(): Boolean
    /** Returns only after installation finishes, never merely on request acceptance. */
    suspend fun install(onProgress: (MlKitModuleState) -> Unit)
    fun close()
}

/** ViewModel-owned, so navigating away or rotating does not restart the download. */
internal class MlKitModuleController(private val installer: MlKitModuleInstaller, private val scope: CoroutineScope) {
    private val mutableState = MutableStateFlow(MlKitModuleState())
    val state = mutableState.asStateFlow()
    private var operation: Job? = null

    fun refresh() {
        if (operation?.isActive == true) return
        val previousStatus = state.value.status
        if (!state.value.ready) mutableState.value = MlKitModuleState(MlKitModuleStatus.CHECKING)
        operation = scope.launch {
            try {
                val available = installer.available()
                // Keep an actionable failed-download message when the model is still missing.
                if (available) mutableState.value = MlKitModuleState(MlKitModuleStatus.READY)
                else mutableState.value = MlKitModuleState(
                    if (previousStatus == MlKitModuleStatus.FAILED) MlKitModuleStatus.FAILED
                    else MlKitModuleStatus.NOT_INSTALLED)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: MlKitServicesUnavailable) { mutableState.value = MlKitModuleState(MlKitModuleStatus.UNAVAILABLE) }
            catch (_: Exception) { mutableState.value = MlKitModuleState(MlKitModuleStatus.FAILED) }
        }
    }

    fun download() {
        if (operation?.isActive == true || state.value.ready) return
        mutableState.value = MlKitModuleState(MlKitModuleStatus.DOWNLOADING)
        operation = scope.launch {
            try {
                if (!installer.available()) {
                    installer.install { mutableState.value = it }
                }
                // Check again, including after an "already installed" response.
                check(installer.available()) { "ML Kit module is not ready" }
                mutableState.value = MlKitModuleState(MlKitModuleStatus.READY)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: MlKitServicesUnavailable) { mutableState.value = MlKitModuleState(MlKitModuleStatus.UNAVAILABLE) }
            catch (_: Exception) { mutableState.value = MlKitModuleState(MlKitModuleStatus.FAILED) }
        }
    }

    fun close() {
        operation?.cancel()
        installer.close()
    }
}

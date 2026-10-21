package com.poetscorner.zatcaqrcodescanner.scanner

import android.content.Context
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailabilityLight
import com.google.android.gms.common.moduleinstall.InstallStatusListener
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.android.gms.common.moduleinstall.ModuleInstallStatusUpdate.InstallState.*
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class GoogleMlKitModuleInstaller(private val context: Context) : MlKitModuleInstaller {
    private val client = ModuleInstall.getClient(context)
    private val scanner = BarcodeScanning.getClient(BarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_QR_CODE).build())
    private val listeners = mutableSetOf<InstallStatusListener>()

    override suspend fun available(): Boolean {
        if (GoogleApiAvailabilityLight.getInstance().isGooglePlayServicesAvailable(context) != ConnectionResult.SUCCESS) {
            throw MlKitServicesUnavailable()
        }
        return client.areModulesAvailable(scanner).awaitModuleTask().areModulesAvailable()
    }

    override suspend fun install(onProgress: (MlKitModuleState) -> Unit) {
        suspendCancellableCoroutine<Unit> { continuation ->
            var session: Int? = null
            lateinit var listener: InstallStatusListener
            fun unregister() {
                listeners.remove(listener)
                client.unregisterListener(listener)
            }
            fun fail(error: Exception) {
                unregister()
                if (continuation.isActive) continuation.resumeWithException(error)
            }
            listener = InstallStatusListener { update ->
                if (!continuation.isActive || (session != null && session != update.sessionId)) return@InstallStatusListener
                session = update.sessionId
                val info = update.progressInfo
                val progress = info?.takeIf { it.totalBytesToDownload > 0 }?.let {
                    (it.bytesDownloaded.toDouble() / it.totalBytesToDownload).toFloat().coerceIn(0f, 1f)
                }
                when (update.installState) {
                    STATE_COMPLETED -> {
                        unregister()
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                    STATE_FAILED, STATE_CANCELED -> fail(IllegalStateException("Module installation failed: ${update.errorCode}"))
                    STATE_DOWNLOAD_PAUSED -> onProgress(MlKitModuleState(MlKitModuleStatus.PAUSED, progress))
                    STATE_INSTALLING -> onProgress(MlKitModuleState(MlKitModuleStatus.INSTALLING, progress))
                    else -> onProgress(MlKitModuleState(MlKitModuleStatus.DOWNLOADING, progress))
                }
            }
            listeners.add(listener)
            continuation.invokeOnCancellation { unregister() }
            val request = ModuleInstallRequest.newBuilder().addApi(scanner).setListener(listener).build()
            client.installModules(request).addOnSuccessListener { response ->
                // The request may complete after cancellation; remove any late registration too.
                if (!continuation.isActive) unregister()
                else if (response.areModulesAlreadyInstalled()) {
                    unregister()
                    continuation.resume(Unit)
                } else session = response.sessionId
            }.addOnFailureListener { fail(it) }
        }
    }

    override fun close() {
        listeners.toList().forEach { client.unregisterListener(it) }
        listeners.clear()
        scanner.close()
    }
}

private suspend fun <T> Task<T>.awaitModuleTask(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        if (continuation.isActive) {
            if (task.isSuccessful) continuation.resume(task.result)
            else continuation.resumeWithException(task.exception ?: IllegalStateException("Module request cancelled"))
        }
    }
}

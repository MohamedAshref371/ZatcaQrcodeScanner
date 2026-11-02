package com.poetscorner.invoiceqr

import android.os.Bundle
import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.poetscorner.invoiceqr.ui.ScannerApp
import com.poetscorner.invoiceqr.ui.ScannerViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var scannerViewModel: ScannerViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val vm = ViewModelProvider(this)[ScannerViewModel::class.java]
        scannerViewModel = vm
        val images = if (savedInstanceState == null) intent.sharedImageUris() else emptyList()
        if (images.isNotEmpty()) vm.galleryImages(images)
        if (vm.screen.value == 0 && images.isEmpty() &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            runCatching { ProcessCameraProvider.getInstance(applicationContext) }
        }
        lifecycleScope.launch {
            vm.settingsLoaded.first { it }
            setContent { ScannerApp(vm = vm) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        scannerViewModel.galleryImages(intent.sharedImageUris())
    }
}


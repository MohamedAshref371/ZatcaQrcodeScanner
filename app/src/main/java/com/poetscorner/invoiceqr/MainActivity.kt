package com.poetscorner.invoiceqr

import android.os.Bundle
import android.content.Intent
import android.view.ViewTreeObserver
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.poetscorner.invoiceqr.ui.ScannerApp
import com.poetscorner.invoiceqr.ui.ScannerViewModel

class MainActivity : AppCompatActivity() {
    private lateinit var scannerViewModel: ScannerViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.enableEdgeToEdge(window)
        val vm = ViewModelProvider(this)[ScannerViewModel::class.java]
        scannerViewModel = vm
        if (savedInstanceState == null) vm.galleryImages(intent.sharedImageUris())
        var contentReady = false
        val content = findViewById<android.view.View>(android.R.id.content)
        // Keep the starting window until Compose has applied the saved appearance.
        content.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (!contentReady) return false
                content.viewTreeObserver.removeOnPreDrawListener(this)
                return true
            }
        })
        setContent {
            val settingsLoaded by vm.settingsLoaded.collectAsStateWithLifecycle()
            if (settingsLoaded) {
                ScannerApp(vm = vm)
                SideEffect { contentReady = true }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        scannerViewModel.galleryImages(intent.sharedImageUris())
    }
}


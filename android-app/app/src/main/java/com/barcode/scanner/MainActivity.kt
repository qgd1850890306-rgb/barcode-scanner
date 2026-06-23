package com.barcode.scanner

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.barcode.scanner.ui.BarcodeScannerApp
import com.barcode.scanner.ui.theme.BarcodeScannerTheme

class MainActivity : ComponentActivity() {

    lateinit var viewModel: ScannerViewModel

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { viewModel.scanImageFile(this, it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this)[ScannerViewModel::class.java]

        setContent {
            BarcodeScannerTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    BarcodeScannerApp(
                        viewModel = viewModel,
                        onPickImage = { pickImageLauncher.launch("image/*") },
                        onOpenUrl = { url ->
                            try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) {}
                        },
                        onShare = { text ->
                            try {
                                startActivity(Intent.createChooser(
                                    Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text)
                                    }, "分享扫码结果"
                                ))
                            } catch (_: Exception) {}
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) { requestPermissions(arrayOf(android.Manifest.permission.CAMERA), 100) }
    }

    override fun onPause() { super.onPause(); if (isFinishing) viewModel.stopCamera() }
    override fun onDestroy() { super.onDestroy(); viewModel.releaseCamera() }
}

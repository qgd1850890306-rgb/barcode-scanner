package com.barcode.scanner

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

    private lateinit var viewModel: ScannerViewModel

    // 图片选择器
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.scanImageFile(this, it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel = ViewModelProvider(this)[ScannerViewModel::class.java]

        setContent {
            BarcodeScannerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BarcodeScannerApp(
                        viewModel = viewModel,
                        onPickImage = { pickImageLauncher.launch("image/*") }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 检查摄像头权限
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.CAMERA), 100)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.releaseCamera()
    }
}

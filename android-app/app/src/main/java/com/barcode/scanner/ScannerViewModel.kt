package com.barcode.scanner

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Size
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.*
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.concurrent.Executors

class ScannerViewModel(application: Application) : AndroidViewModel(application) {

    private val _scanResults = MutableStateFlow<List<ScanRecord>>(emptyList())
    val scanResults: StateFlow<List<ScanRecord>> = _scanResults.asStateFlow()

    val historyRecords: StateFlow<List<ScanRecord>>
    private val _historyRecords = MutableStateFlow<List<ScanRecord>>(emptyList())

    val isScanning = MutableStateFlow(false)
    val statusText = MutableStateFlow("就绪")
    val statusFormat = MutableStateFlow("")
    val isTorchOn = MutableStateFlow(false)
    val selectedResult = MutableStateFlow<ScanRecord?>(null)

    private var camera: Camera? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var barcodeScanner: BarcodeScanner? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private val resultsList = mutableListOf<ScanRecord>()
    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private val db = AppDatabase.getInstance(application)
    private val dao = db.scanHistoryDao()
    private val appContext = application

    private var lastScanTime = 0L
    private var lastScanData = ""

    init {
        barcodeScanner = BarcodeScanning.getClient()
        historyRecords = dao.getAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    fun toggleTorch() {
        try {
            camera?.cameraControl?.enableTorch(!isTorchOn.value)
            isTorchOn.value = !isTorchOn.value
        } catch (e: Exception) {
            statusFormat.value = "⚠️ 手电筒不可用"
        }
    }

    fun startCamera(context: Context, lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().apply {
                setSurfaceProvider(previewView.surfaceProvider)
            }
            imageAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(1280, 720))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build().apply {
                    setAnalyzer(analysisExecutor) { imageProxy -> processImage(imageProxy) }
                }
            try {
                cameraProvider?.unbindAll()
                camera = cameraProvider?.bindToLifecycle(
                    lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis
                )
                isScanning.value = true
                statusText.value = "✅ 摄像头已开启"
                statusFormat.value = "实时扫码中..."
            } catch (e: Exception) {
                statusText.value = "❌ 摄像头启动失败"
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun stopCamera() {
        try {
            cameraProvider?.unbindAll()
            if (isTorchOn.value) { camera?.cameraControl?.enableTorch(false); isTorchOn.value = false }
        } catch (_: Exception) {}
        isScanning.value = false; camera = null; imageAnalysis = null
    }

    private fun processImage(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            barcodeScanner?.process(inputImage)
                ?.addOnSuccessListener { barcodes ->
                    for (b in barcodes) {
                        b.rawValue?.let { addResult(it, formatName(b.format)) }
                    }
                }?.addOnCompleteListener { imageProxy.close() }
        } else { imageProxy.close() }
    }

    fun scanImageFile(context: Context, uri: Uri) {
        statusText.value = "正在扫码..."
        try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream); inputStream?.close()
            if (bitmap != null) {
                val inputImage = InputImage.fromBitmap(bitmap, 0)
                barcodeScanner?.process(inputImage)
                    ?.addOnSuccessListener { barcodes ->
                        for (barcode in barcodes) {
                            val rawValue = barcode.rawValue ?: continue
                            addResult(rawValue, formatName(barcode.format))
                        }
                        if (barcodes.isEmpty()) statusFormat.value = "未识别到条码"
                    }
                    ?.addOnFailureListener { statusFormat.value = "扫码失败: ${it.message?.take(50)}" }
            }
        } catch (e: Exception) { statusFormat.value = "扫码失败: ${e.message?.take(50)}" }
        statusText.value = "就绪"
    }

    private fun addResult(data: String, format: String) {
        val now = System.currentTimeMillis()
        if (data == lastScanData && now - lastScanTime < 5000) return
        lastScanData = data; lastScanTime = now
        if (resultsList.none { it.data == data }) {
            val isUrl = data.startsWith("http://") || data.startsWith("https://")
            val record = ScanRecord(data = data, format = format, isUrl = isUrl, timestamp = now)
            resultsList.add(0, record)
            _scanResults.value = resultsList.toList()
            statusFormat.value = "✅ 识别到 $format"
            vibrate()
            copyToClipboard(data)
            viewModelScope.launch { dao.insert(record) }
        }
    }

    fun copyToClipboard(text: String) {
        try {
            val clip = ClipData.newPlainText("barcode", text)
            val clipboard = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(clip)
        } catch (_: Exception) {}
    }

    private fun vibrate() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else { @Suppress("DEPRECATION") appContext.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                vibrator.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
            else @Suppress("DEPRECATION") vibrator.vibrate(80)
        } catch (_: Exception) {}
    }

    fun selectResult(record: ScanRecord) { selectedResult.value = record }
    fun clearSelectedResult() { selectedResult.value = null }

    fun deleteHistoryRecord(record: ScanRecord) {
        viewModelScope.launch {
            dao.delete(record)
            resultsList.removeAll { it.data == record.data && it.timestamp == record.timestamp }
            _scanResults.value = resultsList.toList()
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            dao.clearAll(); resultsList.clear()
            _scanResults.value = emptyList()
        }
    }

    fun clearResults() { resultsList.clear(); _scanResults.value = emptyList(); statusFormat.value = "已清空" }

    fun releaseCamera() { stopCamera(); barcodeScanner?.close(); analysisExecutor.shutdown() }

    private fun formatName(format: Int) = when (format) {
        Barcode.FORMAT_QR_CODE -> "QRCode"
        Barcode.FORMAT_DATA_MATRIX -> "DataMatrix"
        Barcode.FORMAT_CODE_128 -> "Code128"
        Barcode.FORMAT_CODE_39 -> "Code39"
        Barcode.FORMAT_EAN_13 -> "EAN13"
        Barcode.FORMAT_EAN_8 -> "EAN8"
        Barcode.FORMAT_UPC_A -> "UPCA"
        Barcode.FORMAT_UPC_E -> "UPCE"
        Barcode.FORMAT_CODABAR -> "Codabar"
        Barcode.FORMAT_ITF -> "ITF"
        Barcode.FORMAT_PDF417 -> "PDF417"
        Barcode.FORMAT_AZTEC -> "Aztec"
        else -> "Unknown"
    }
}

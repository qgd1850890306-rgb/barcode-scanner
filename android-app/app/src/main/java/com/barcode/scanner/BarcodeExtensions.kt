package com.barcode.scanner

import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.barcode.BarcodeScanner
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// 为 ML Kit BarcodeScanner 添加 await 扩展
suspend fun BarcodeScanner.await(image: InputImage): List<Barcode>? {
    return suspendCancellableCoroutine { continuation ->
        process(image)
            .addOnSuccessListener { barcodes ->
                continuation.resume(barcodes)
            }
            .addOnFailureListener { e ->
                continuation.resumeWithException(e)
            }
    }
}

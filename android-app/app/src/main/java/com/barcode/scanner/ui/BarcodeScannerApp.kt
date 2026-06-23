package com.barcode.scanner.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.camera.view.PreviewView
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.barcode.scanner.ScanRecord
import com.barcode.scanner.ScannerViewModel
import com.barcode.scanner.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarcodeScannerApp(
    viewModel: ScannerViewModel,
    onPickImage: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onShare: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.ViewTreeLifecycleOwner.get(context)!!
    val scanResults by viewModel.scanResults.collectAsState()
    val historyRecords by viewModel.historyRecords.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val statusText by viewModel.statusText.collectAsState()
    val statusFormat by viewModel.statusFormat.collectAsState()
    val isTorchOn by viewModel.isTorchOn.collectAsState()
    val selectedResult by viewModel.selectedResult.collectAsState()

    var showCamera by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }

    val hasCameraPermission = remember {
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    selectedResult?.let { record ->
        ResultDetailDialog(
            record = record, onDismiss = { viewModel.clearSelectedResult() },
            onCopy = { viewModel.copyToClipboard(record.data) },
            onOpenUrl = { if (record.isUrl) onOpenUrl(record.data) },
            onShare = { onShare(record.data) },
            onDelete = { viewModel.deleteHistoryRecord(record); viewModel.clearSelectedResult() }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Column {
                    Text("📷 条码扫码器", fontWeight = FontWeight.Bold)
                    Text("QR · DataMatrix · Code128", fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
                }},
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Primary, titleContentColor = Color.White),
                actions = {
                    if (historyRecords.isNotEmpty()) {
                        IconButton(onClick = { showHistory = !showHistory }) {
                            Icon(if (showHistory) Icons.Default.HistoryToggleOff else Icons.Default.History, "历史", tint = Color.White)
                        }
                    }
                    if (scanResults.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearResults() }) {
                            Icon(Icons.Default.Delete, "清空", tint = Color.White)
                        }
                    }
                }
            )
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Column {
                    Row(modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { if (hasCameraPermission) showCamera = !showCamera },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = if (showCamera) Danger else Primary),
                            enabled = hasCameraPermission) {
                            Icon(if (showCamera) Icons.Default.VideocamOff else Icons.Default.Videocam, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp)); Text(if (showCamera) "关闭" else "摄像头", fontSize = 13.sp)
                        }
                        if (showCamera) {
                            Button(onClick = { viewModel.toggleTorch() }, modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = if (isTorchOn) Color(0xFFFF9800) else Primary)) {
                                Icon(if (isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp)); Text(if (isTorchOn) "关灯" else "手电", fontSize = 13.sp)
                            }
                        }
                        Button(onClick = onPickImage, modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Primary)) {
                            Icon(Icons.Default.PhotoLibrary, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp)); Text("相册", fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).background(Background)) {
            // 摄像头预览区
            if (showCamera && hasCameraPermission) {
                Box(modifier = Modifier.fillMaxWidth().height(260.dp).padding(8.dp).clip(RoundedCornerShape(12.dp))) {
                    AndroidView(factory = { ctx -> PreviewView(ctx).also { viewModel.startCamera(ctx, lifecycleOwner, it) } }, modifier = Modifier.fillMaxSize())
                    Box(modifier = Modifier.fillMaxSize().border(2.dp, Primary.copy(alpha = 0.5f), RoundedCornerShape(12.dp))) {
                        Box(modifier = Modifier.align(Alignment.Center).width(200.dp).height(2.dp).background(Primary.copy(alpha = 0.8f)))
                    }
                }
            } else if (showCamera) {
                Card(modifier = Modifier.fillMaxWidth().height(200.dp).padding(8.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))) {
                    Box(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Warning, null, tint = Color(0xFFFF9800), modifier = Modifier.size(40.dp))
                            Spacer(Modifier.height(8.dp)); Text("需要摄像头权限", color = Color(0xFFE65100))
                        }
                    }
                }
            }

            // 状态栏
            if (showCamera || scanResults.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFAFAFA))) {
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(statusText, fontSize = 12.sp, color = TextSecondary)
                        Text(statusFormat, fontSize = 12.sp, color = if (statusFormat.startsWith("✅")) Success else TextSecondary)
                    }
                }
            }

            // 结果/历史卡片
            Card(modifier = Modifier.fillMaxWidth().weight(1f).padding(8.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column {
                    TabRow(selectedTabIndex = if (showHistory) 1 else 0, containerColor = Color.White, contentColor = Primary) {
                        Tab(selected = !showHistory, onClick = { showHistory = false }, text = { Text("📱 本次结果", fontSize = 13.sp) })
                        Tab(selected = showHistory, onClick = { showHistory = true }, text = { Text("📚 历史 (${historyRecords.size})", fontSize = 13.sp) })
                    }
                    if (showHistory) HistoryContent(historyRecords, onSelect = { viewModel.selectResult(it) }, onDelete = { viewModel.deleteHistoryRecord(it) }, onClearAll = { viewModel.clearAllHistory() })
                    else ResultContent(scanResults, onSelect = { viewModel.selectResult(it) })
                }
            }
        }
    }
}

@Composable
fun ResultContent(results: List<ScanRecord>, onSelect: (ScanRecord) -> Unit) {
    if (results.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🔍", fontSize = 40.sp); Spacer(Modifier.height(8.dp))
                Text("暂无扫码结果", color = TextSecondary, fontSize = 14.sp)
                Spacer(Modifier.height(4.dp)); Text("扫码后结果将显示在这里", color = TextSecondary, fontSize = 12.sp)
            }
        }
    } else {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
            items(results, key = { "${it.data}_${it.timestamp}" }) { ScanResultItem(it, onClick = { onSelect(it) }) }
        }
    }
}

@Composable
fun HistoryContent(records: List<ScanRecord>, onSelect: (ScanRecord) -> Unit, onDelete: (ScanRecord) -> Unit, onClearAll: () -> Unit) {
    if (records.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("暂无历史记录", color = TextSecondary, fontSize = 14.sp) }
    } else {
        Column {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onClearAll) {
                    Icon(Icons.Default.DeleteSweep, "清空全部", Modifier.size(16.dp)); Spacer(Modifier.width(4.dp))
                    Text("清空全部", fontSize = 12.sp, color = Danger)
                }
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                items(records, key = { it.id }) { ScanResultItem(it, onClick = { onSelect(it) }, showDelete = true, onDelete = { onDelete(it) }) }
            }
        }
    }
}

@Composable
fun ScanResultItem(record: ScanRecord, onClick: () -> Unit, showDelete: Boolean = false, onDelete: (() -> Unit)? = null) {
    val isQR = record.format == "QRCode"
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FF)),
        shape = RoundedCornerShape(8.dp),
        border = CardDefaults.outlinedCardBorder().copy(width = 1.dp, brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFE8ECFF)))
    ) {
        Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(4.dp), color = if (isQR) BadgeQR else BadgeDM) {
                        Text(record.format, Modifier.padding(horizontal = 8.dp, vertical = 2.dp), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = if (isQR) BadgeQRText else BadgeDMText)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(dateFormat.format(Date(record.timestamp)), fontSize = 11.sp, color = TextSecondary)
                    if (record.isUrl) { Spacer(Modifier.width(4.dp)); Text("🔗", fontSize = 11.sp) }
                }
                Spacer(Modifier.height(4.dp))
                Surface(shape = RoundedCornerShape(4.dp), color = Color.White,
                    border = CardDefaults.outlinedCardBorder().copy(width = 1.dp, brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFEEEEEE)))) {
                    Text(record.data, Modifier.fillMaxWidth().padding(8.dp), fontSize = 13.sp, fontFamily = FontFamily.Monospace, color = TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (showDelete && onDelete != null) {
                IconButton(onClick = onDelete, Modifier.size(28.dp)) { Icon(Icons.Default.Close, "删除", Modifier.size(16.dp), tint = Danger.copy(alpha = 0.6f)) }
            }
            Icon(Icons.Default.ChevronRight, "详情", Modifier.size(20.dp), tint = TextSecondary.copy(alpha = 0.5f))
        }
    }
}

@Composable
fun ResultDetailDialog(record: ScanRecord, onDismiss: () -> Unit, onCopy: () -> Unit, onOpenUrl: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(4.dp), color = if (record.format == "QRCode") BadgeQR else BadgeDM) {
                Text(record.format, Modifier.padding(horizontal = 8.dp, vertical = 2.dp), fontSize = 12.sp, color = if (record.format == "QRCode") BadgeQRText else BadgeDMText, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.width(8.dp)); Text("扫码详情", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }},
        text = { Column {
            Text("⏱ ${dateFormat.format(Date(record.timestamp))}", fontSize = 12.sp, color = TextSecondary)
            Spacer(Modifier.height(8.dp))
            Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFF5F5F5), border = BorderStroke(1.dp, Color(0xFFE0E0E0))) {
                SelectionContainer { Text(record.data, Modifier.fillMaxWidth().padding(12.dp), fontSize = 14.sp, fontFamily = FontFamily.Monospace, color = TextPrimary) }
            }
            Spacer(Modifier.height(4.dp)); Text("👆 长按可选中复制", fontSize = 11.sp, color = TextSecondary)
            if (record.isUrl) { Spacer(Modifier.height(8.dp)); Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFFE3F2FD)) { Text("🔗 这是一个链接", Modifier.padding(8.dp), fontSize = 12.sp, color = Color(0xFF1565C0)) } }
        }},
        confirmButton = { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            TextButton(onClick = onCopy) { Icon(Icons.Default.ContentCopy, "复制", Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("复制") }
            if (record.isUrl) TextButton(onClick = onOpenUrl) { Icon(Icons.Default.OpenInBrowser, "打开", Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("打开") }
            TextButton(onClick = onShare) { Icon(Icons.Default.Share, "分享", Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("分享") }
            TextButton(onClick = onDelete, colors = ButtonDefaults.textButtonColors(contentColor = Danger)) { Icon(Icons.Default.Delete, "删除", Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("删除") }
        }},
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

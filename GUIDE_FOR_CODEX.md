# DM码扫码器 — Codex 改进指导 v2

> 来源：Hermes Agent 验收审核
> 日期：2026-06-22 (更新 v2)
> 项目：`D:\barcode-scanner`
> GitHub：`qgd1850890306-rgb/barcode-scanner`

---

## 一、历史回顾

之前 Hermes 编译了 v1.0 APK（383K，Kotlin+Compose+ML Kit+CameraX），但用户反馈**"产出的不行"**。

### 已有的（v1.0 已有功能）

| 功能 | 状态 | 说明 |
|:----|:----:|:-----|
| 摄像头预览 | ✅ | CameraX PreviewView |
| 实时扫码（QR+DataMatrix） | ✅ | ML Kit BarcodeScanning |
| 相册选图扫描 | ✅ | ActivityResultContracts.GetContent |
| 结果列表+复制 | ✅ | LazyColumn + ClipboardManager |
| 格式标签（QR/DataMatrix 徽章） | ✅ | BadgeQR/BadgeDM 颜色区分 |
| Material 3 主题 | ✅ | TopAppBar + Scaffold |
| 扫码去重 | ✅ | 去重逻辑（但弱，可改进） |

### 需要新增的（v2.0 改进项目）

| 优先级 | 功能 | 难度 | 说明 |
|:-----:|:----|:----:|:-----|
| 🔴 P0 | **SQLite 历史持久化** | 中 | Room 数据库，扫描记录不丢失 |
| 🔴 P0 | **详情弹窗** | 低 | AlertDialog 显示格式/时间/内容/操作 |
| 🟡 P1 | **手电筒/闪光灯** | 低 | CameraX camera.cameraControl.enableTorch() |
| 🟡 P1 | **震动反馈** | 低 | Vibrator + VibrationEffect |
| 🟡 P1 | **自动复制到剪贴板** | 低 | 扫码成功自动复制 |
| 🟡 P1 | **链接识别+打开** | 低 | Intent.ACTION_VIEW 打开 URL |
| 🟢 P2 | **历史/结果标签切换** | 低 | TabRow 切换视图 |
| 🟢 P2 | **分享功能** | 低 | Intent.ACTION_SEND |
| 🟢 P2 | **删除单条记录** | 低 | 长按/按钮删除 |

---

## 二、技术方案

### 架构
```
当前: Kotlin + Compose + ML Kit + CameraX + Material3
目标: Kotlin + Compose + ML Kit + CameraX + Material3 + Room

保持不变：
- Kotlin ✅ (不要改成 Java，ML Kit 已有 Kotlin 代码)
- Compose ✅ (UI 已有完整结构)
- ML Kit ✅ (扫码引擎)
- CameraX ✅ (摄像头)

新增：
- Room (SQLite 持久化)
- lifecycle-viewmodel-compose (已部分有)
- lifecycle-runtime-compose (已部分有)
```

⚠️ **注意**: 不要全部重写。v1.0 的 Compose UI 已经有一个可用的框架，只需要**增量添加功能**。

### 新增文件

```
android-app/app/src/main/java/com/barcode/scanner/
├── ScanRecord.kt          ← 新增: Room 实体 (id, data, format, isUrl, timestamp)
├── ScanHistoryDao.kt      ← 新增: Room DAO
├── AppDatabase.kt         ← 新增: Room Database 单例
```

### 修改文件

```
android-app/app/src/main/java/com/barcode/scanner/
├── ScannerViewModel.kt    ← 修改: 添加 Room 集成、手电筒、震动、复制
├── MainActivity.kt        ← 修改: 添加 onOpenUrl/onShare 回调

android-app/app/src/main/java/com/barcode/scanner/ui/
├── BarcodeScannerApp.kt   ← 修改: 添加详情弹窗、历史标签、手电筒按钮
├── theme/Color.kt         ← 修改: 添加 Success/Danger 颜色

android-app/app/build.gradle.kts  ← 修改: 添加 Room + KSP 依赖
android-app/build.gradle.kts      ← 修改: 添加 KSP 插件声明
android-app/app/src/main/AndroidManifest.xml ← 修改: 添加 VIBRATE 权限
```

---

## 三、完整代码实现

### 3.1 ScanRecord.kt — Room 实体

```kotlin
package com.barcode.scanner

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scan_history")
data class ScanRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val data: String,
    val format: String,
    val isUrl: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
```

### 3.2 ScanHistoryDao.kt — Room DAO

```kotlin
package com.barcode.scanner

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanHistoryDao {
    @Query("SELECT * FROM scan_history ORDER BY timestamp DESC")
    fun getAll(): Flow<List<ScanRecord>>

    @Insert
    suspend fun insert(record: ScanRecord): Long

    @Delete
    suspend fun delete(record: ScanRecord)

    @Query("DELETE FROM scan_history")
    suspend fun clearAll()
}
```

### 3.3 AppDatabase.kt — Room 数据库

```kotlin
package com.barcode.scanner

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ScanRecord::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scanHistoryDao(): ScanHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "barcode_scanner.db"
                ).build()
                INSTANCE = instance
            }
        }
    }
}
```

### 3.4 ScannerViewModel.kt — 核心 ViewModel（改造）

**改造要点**：
1. 继承 `AndroidViewModel`（需要 Application context 操作 Room）
2. 添加 `historyRecords` StateFlow（从 Room Flow 收集）
3. 添加 `isTorchOn` / `toggleTorch()`
4. 添加 `selectedResult`（弹窗数据）
5. 添加 `vibrate()` / `copyToClipboard()`
6. 添加 `deleteHistoryRecord()` / `clearAllHistory()`
7. 扫码成功后自动：写数据库 → 震动 → 复制 → 更新列表
8. 修改 `addResult()` 去重逻辑（5秒窗口）
9. 添加 `formatName()` 支持更多条码格式

```kotlin
class ScannerViewModel(application: Application) : AndroidViewModel(application) {
    // 现有代码不变...
    // 新增字段:
    private val db = AppDatabase.getInstance(application)
    private val dao = db.scanHistoryDao()
    
    // 新增 StateFlow:
    val historyRecords = dao.getAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())
    val isTorchOn = MutableStateFlow(false)
    val selectedResult = MutableStateFlow<ScanRecord?>(null)
    
    // 扫描成功自动执行:
    private fun addResult(data: String, format: String) {
        // ...去重逻辑...
        val record = ScanRecord(data = data, format = format, isUrl = isUrl, timestamp = now)
        resultsList.add(0, record)
        _scanResults.value = resultsList.toList()
        
        vibrate()
        copyToClipboard(data)
        viewModelScope.launch { dao.insert(record) }
    }
    
    fun toggleTorch() {
        camera?.cameraControl?.enableTorch(!isTorchOn.value)
        isTorchOn.value = !isTorchOn.value
    }
    
    private fun vibrate() { /* Vibrator + VibrationEffect */ }
    fun copyToClipboard(text: String) { /* ClipboardManager */ }
    fun deleteHistoryRecord(record: ScanRecord) { viewModelScope.launch { dao.delete(record) } }
    fun clearAllHistory() { viewModelScope.launch { dao.clearAll() } }
}
```

### 3.5 BarcodeScannerApp.kt — UI 改造

**改造要点**：
1. 添加 `isTorchOn` 手电筒按钮（仅摄像头开启时显示）
2. 添加 `ResultDetailDialog` 弹窗（点击结果项弹出）
3. 添加 `showHistory` Tab 切换（本次结果 / 历史记录）
4. 历史记录模式：显示全部历史、可删除
5. 结果详情弹窗：显示格式/时间/内容、复制/打开/分享/删除按钮

```kotlin
// 新增 ResultDetailDialog:
@Composable
fun ResultDetailDialog(
    record: ScanRecord,
    onDismiss, onCopy, onOpenUrl, onShare, onDelete: () -> Unit
) {
    AlertDialog(
        title = { Text("${record.format} 扫码详情") },
        text = {
            Column {
                Text("⏱ ${dateFormat.format(Date(record.timestamp))}")
                Surface { SelectionContainer { Text(record.data, fontFamily = FontFamily.Monospace) } }
                if (record.isUrl) Text("🔗 这是一个链接")
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = onCopy) { Text("复制") }
                if (record.isUrl) TextButton(onClick = onOpenUrl) { Text("打开") }
                TextButton(onClick = onShare) { Text("分享") }
                TextButton(onClick = onDelete) { Text("删除", color = Danger) }
            }
        }
    )
}
```

### 3.6 build.gradle.kts 改造

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp") version "1.9.20-1.0.14"  // ← 新增
}

dependencies {
    // 新增 Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
}
```

---

## 四、实现步骤（按顺序）

### Step 1: 创建 3 个新文件
- `ScanRecord.kt` → Room Entity
- `ScanHistoryDao.kt` → Room DAO
- `AppDatabase.kt` → Room Database

### Step 2: 修改 ScannerViewModel.kt
- 继承 `AndroidViewModel`
- 添加 Room 集成
- 添加 torch/copy/vibrate/delete 方法

### Step 3: 修改 BarcodeScannerApp.kt
- 添加手电筒按钮
- 添加详情弹窗
- 添加历史记录 Tab
- 修改函数签名传 onOpenUrl/onShare

### Step 4: 修改 MainActivity.kt
- 添加 onOpenUrl/onShare lambda

### Step 5: 修改 build.gradle.kts
- 添加 Room + KSP 依赖

### Step 6: 修改 AndroidManifest.xml
- 添加 VIBRATE 权限

### Step 7: 本地编译验证
```bash
cd D:\barcode-scanner\android-app
# 或者提交到 GitHub 触发 Actions
```

### Step 8: 推送到 GitHub → Actions 编译
```bash
gh api repos/qgd1850890306-rgb/barcode-scanner/contents/...  # 上传文件
# 或
cd D:\barcode-scanner && git push origin main
```

---

## 五、验收清单

### 功能验收
- [ ] 摄像头扫码 → 结果自动列出来
- [ ] 扫码成功有**震动**反馈
- [ ] 扫码结果**自动复制**到剪贴板
- [ ] 手电筒开关可用
- [ ] 相册选图可扫描
- [ ] 点击结果 → 弹出**详情弹窗**
- [ ] 详情弹窗可**复制/打开链接/分享/删除**
- [ ] 底部按钮区显示**手电筒**（摄像头开启时）
- [ ] 有**本次结果**和**历史记录**标签切换
- [ ] 历史记录**持久化**（重启 App 不丢失）
- [ ] 历史记录可单条删除 / 清空全部
- [ ] 如果是 URL 可打开浏览器
- [ ] APK 编译成功，无崩溃

### 编译验收
- [ ] Gradle 8.5 + JDK 17 编译通过
- [ ] APK 大小 1-5MB
- [ ] GitHub Actions 自动编译通过
- [ ] `assembleDebug` 成功

---

## 六、给 Codex 的最终指令

请按以下流程完成这个项目：

```
1. 阅读本文件全部内容
2. 打开 D:\barcode-scanner 项目
3. 按 Step 1→8 顺序逐步实现
4. 每完成一个文件，立即提交到 GitHub
5. 全部完成后触发 GitHub Actions 编译
6. 下载 APK 验证

GitHub 仓库: qgd1850890306-rgb/barcode-scanner
项目路径: D:\barcode-scanner
主分支: main
```

Codex 完成全部功能后，Hermes 会审核编译结果。

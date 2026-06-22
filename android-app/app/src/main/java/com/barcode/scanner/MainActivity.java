package com.barcode.scanner;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanIntentResult;
import com.journeyapps.barcodescanner.ScanOptions;
import androidx.activity.result.ActivityResultLauncher;

public class MainActivity extends Activity {
    private TextView resultText;
    private ActivityResultLauncher<ScanOptions> barcodeLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Button btn = new Button(this);
        btn.setText("扫描 DM码 / QR码");
        btn.setTextSize(18);
        btn.setPadding(32, 64, 32, 64);
        resultText = new TextView(this);
        resultText.setTextSize(16);
        resultText.setPadding(32, 32, 32, 32);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.addView(btn);
        layout.addView(resultText);
        setContentView(layout);
        barcodeLauncher = registerForActivityResult(new ScanContract(), this::handleResult);
        btn.setOnClickListener(v -> {
            ScanOptions options = new ScanOptions();
            options.setDesiredBarcodeFormats(ScanOptions.DATA_MATRIX, ScanOptions.QR_CODE);
            options.setPrompt("对准 DM码或二维码");
            options.setBeepEnabled(true);
            options.setOrientationLocked(false);
            barcodeLauncher.launch(options);
        });
    }

    private void handleResult(ScanIntentResult result) {
        if (result.getContents() == null) {
            Toast.makeText(this, "取消扫描", Toast.LENGTH_SHORT).show();
        } else {
            String fmt = result.getFormatName();
            String data = result.getContents();
            resultText.setText("格式: " + fmt + "\n\n内容:\n" + data);
            ClipboardManager clip = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            clip.setPrimaryClip(ClipData.newPlainText("barcode", data));
            Toast.makeText(this, "已复制到剪贴板", Toast.LENGTH_SHORT).show();
        }
    }
}

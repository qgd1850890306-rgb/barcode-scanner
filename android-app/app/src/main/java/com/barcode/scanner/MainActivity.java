package com.barcode.scanner;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

public class MainActivity extends Activity {
    private TextView resultText;

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
        btn.setOnClickListener(v -> {
            IntentIntegrator integrator = new IntentIntegrator(this);
            integrator.setDesiredBarcodeFormats(IntentIntegrator.DATA_MATRIX, IntentIntegrator.QR_CODE);
            integrator.setPrompt("对准 DM码或二维码");
            integrator.setBeepEnabled(true);
            integrator.setOrientationLocked(false);
            integrator.initiateScan();
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result == null || result.getContents() == null) {
            Toast.makeText(this, "取消扫描", Toast.LENGTH_SHORT).show();
        } else {
            String fmt = result.getFormatName();
            String content = result.getContents();
            resultText.setText("格式: " + fmt + "\n\n内容:\n" + content);
            ClipboardManager clip = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            clip.setPrimaryClip(ClipData.newPlainText("barcode", content));
            Toast.makeText(this, "已复制到剪贴板", Toast.LENGTH_SHORT).show();
        }
        super.onActivityResult(requestCode, resultCode, data);
    }
}

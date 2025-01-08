package com.example.myocrapplication;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import androidx.appcompat.app.AppCompatActivity;

public class SettingActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setting);

        // Server Setting
        EditText editTextLambdaUrl = findViewById(R.id.editTextLambdaUrl);
        EditText editTextCgi1Url = findViewById(R.id.editTextCgi1Url);
        EditText editTextCgi2Url = findViewById(R.id.editTextCgi2Url);
        EditText editTextAccount = findViewById(R.id.editTextAccount);

        // for Quick Registration
        EditText editTextQuickShop    = findViewById(R.id.editTextQuickShop);
        EditText editTextQuickPayment = findViewById(R.id.editTextQuickPayment);
        EditText editTextQuickItems   = findViewById(R.id.editTextQuickItems);

        Button buttonSaveSettings = findViewById(R.id.buttonSaveSettings);

        // SharedPreferences を取得
        SharedPreferences preferences = getSharedPreferences("AppSettings", MODE_PRIVATE);

        // 設定をロード
        editTextLambdaUrl.setText(preferences.getString("LambdaUrl", ""));
        editTextCgi1Url.setText(preferences.getString("CgiUrl", ""));
        editTextCgi2Url.setText(preferences.getString("Cgi2Url", ""));
        editTextAccount.setText(preferences.getString("Account", ""));
        editTextQuickShop.setText(preferences.getString("QuickShop", ""));
        editTextQuickPayment.setText(preferences.getString("QuickPayment", ""));
        editTextQuickItems.setText(preferences.getString("QuickItems", ""));

        // 保存ボタンの動作
        buttonSaveSettings.setOnClickListener(v -> {
            SharedPreferences.Editor editor = preferences.edit();
            editor.putString("LambdaUrl", editTextLambdaUrl.getText().toString());
            editor.putString("CgiUrl",  editTextCgi1Url.getText().toString());
            editor.putString("Cgi2Url", editTextCgi2Url.getText().toString());
            editor.putString("Account", editTextAccount.getText().toString());
            editor.putString("QuickShop", editTextQuickShop.getText().toString());
            editor.putString("QuickPayment", editTextQuickPayment.getText().toString());
            editor.putString("QuickItems", editTextQuickItems.getText().toString());
            editor.apply(); // 設定を保存
            finish(); // 画面を閉じる
        });
    }
}

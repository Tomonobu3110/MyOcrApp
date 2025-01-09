package com.example.myocrapplication;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

public class ConfirmActivity extends Activity {
    private EditText editTextStore;
    private EditText editTextDate;
    private EditText editTextPayment;
    private EditText editTextItem;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_confirm);

        // EditTexts
        editTextStore = findViewById(R.id.editTextStore);
        editTextDate = findViewById(R.id.editTextDate);
        editTextPayment = findViewById(R.id.editTextPayment);
        editTextItem = findViewById(R.id.editTextItem);

        // Buttons
        Button buttonToday = findViewById(R.id.buttonToday);
        Button buttonSubmit = findViewById(R.id.buttonSubmit);
        Button buttonSubmitOmm = findViewById(R.id.buttonSubmitOmm);
        Button buttonSubmitMmmAndOmm = findViewById(R.id.buttonSubmitMmmAndOmm);
        Button buttonCancel = findViewById(R.id.buttonCancel);

        // 送信ボタンのクリックリスナー(個人)
        buttonSubmit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SharedPreferences preferences = getSharedPreferences("AppSettings", MODE_PRIVATE);
                String cgiUrl = preferences.getString("CgiUrl", "");
                checkAndSubmit(cgiUrl, "private", false);
            }
        });

        // 送信ボタンのクリックリスナー(立替)
        buttonSubmitMmmAndOmm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SharedPreferences preferences = getSharedPreferences("AppSettings", MODE_PRIVATE);
                String cgiUrl  = preferences.getString("CgiUrl", "");
                String cgi2Url = preferences.getString("Cgi2Url", "");
                String account = preferences.getString("Account", "");
                checkAndSubmit(cgiUrl, "public", true); // 個人に送信済みで登録
                checkAndSubmit(cgi2Url, account, false); // 家計に登録
            }
        });

        // 送信ボタンのクリックリスナー(家計)
        buttonSubmitOmm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SharedPreferences preferences = getSharedPreferences("AppSettings", MODE_PRIVATE);
                String cgi2Url = preferences.getString("Cgi2Url", "");
                String account = preferences.getString("Account", "");
                checkAndSubmit(cgi2Url, "public", false); // 家計に登録
            }
        });

        // キャンセルボタンのクリックリスナー
        buttonCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish(); // アクティビティを閉じる
            }
        });

        // 今日ボタンのリスナ
        buttonToday.setOnClickListener(v -> {
            // 現在の日付を取得
            Calendar calendar = Calendar.getInstance();
            int year = calendar.get(Calendar.YEAR);
            int month = calendar.get(Calendar.MONTH) + 1;  // 月は0から始まるので+1
            int day = calendar.get(Calendar.DAY_OF_MONTH);

            // フォーマットしてEditTextにセット
            String formattedDate = String.format("%04d年%02d月%02d日", year, month, day);
            editTextDate.setText(formattedDate);
        });

        // 文字列の受け取り
        String jsonData = getIntent().getStringExtra("response_json");
        if (jsonData != null) {
            try {
                // JSON文字列を解析
                JSONObject json = new JSONObject(jsonData);
                String shop = json.optString("shop", "");
                String date = json.optString("date", "");
                String payment = json.optString("payment", "");
                JSONArray items = json.optJSONArray("items");

                // アイテムリストを文字列に結合
                StringBuilder itemsString = new StringBuilder();
                if (items != null) {
                    for (int i = 0; i < items.length(); i++) {
                        if (i > 0) itemsString.append(", "); // 区切り文字
                        itemsString.append(items.optString(i, ""));
                    }
                }

                // 各入力欄に値を設定
                editTextStore.setText(shop);
                editTextDate.setText(date);
                editTextPayment.setText(payment);
                editTextItem.setText(itemsString.toString());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void checkAndSubmit(String cgiUrl, String account, Boolean sent) {
        String store   = editTextStore.getText().toString();
        String date_in = editTextDate.getText().toString();
        String payment = editTextPayment.getText().toString();
        String item    = editTextItem.getText().toString();

        SimpleDateFormat inputFormat  = new SimpleDateFormat("yyyy年MM月dd日");
        SimpleDateFormat outputFormat = new SimpleDateFormat("yyyy/MM/dd");
        String date_out;
        try {
            // 入力文字列を Date に変換
            Date date_org = inputFormat.parse(date_in);

            // もし yyyy が2000未満なら2000を足す
            Calendar cal = Calendar.getInstance();
            cal.setTime(date_org);
            if (cal.get(Calendar.YEAR) < 2000) {
                cal.add(Calendar.YEAR, 2000);
                date_out = outputFormat.format(cal.getTime());
            } 
            else {
                // Date を出力フォーマットの文字列に変換
                date_out = outputFormat.format(date_org);
            }
        } catch (ParseException e) {
            e.printStackTrace();
            Toast.makeText(ConfirmActivity.this, "日付のフォーマットが不正です", Toast.LENGTH_SHORT).show();
            return;
        }

        // 入力値を確認
        if (store.isEmpty() || date_out.isEmpty() || payment.isEmpty() || item.isEmpty()) {
            Toast.makeText(ConfirmActivity.this, "全ての項目を入力してください", Toast.LENGTH_SHORT).show();
        } else {
            SharedPreferences preferences = getSharedPreferences("AppSettings", MODE_PRIVATE);
            //String lambdaUrl = preferences.getString("LambdaUrl", ""); // デフォルトは空文字列
            //String cgiUrl = preferences.getString("CgiUrl", "");

            if (cgiUrl.isEmpty()) {
                Toast.makeText(ConfirmActivity.this, "CGI URL が設定されていません", Toast.LENGTH_SHORT).show();
                return;
            }

            if (account.isEmpty()) {
                Toast.makeText(ConfirmActivity.this, "登録名が設定されていません", Toast.LENGTH_SHORT).show();
                return;
            }

            // フォーム送信処理
            new Thread(() -> {
                try {
                    FormSubmitter submitter = new FormSubmitter(cgiUrl);
                    // パラメータを追加
                    submitter.addField("dtkind", date_out); // 入出金日
                    submitter.addField("date", date_out); // 指定日
                    submitter.addField("group1", "0"); // 分類
                    submitter.addField("group2", "");
                    submitter.addField("detail", encodeToEucJp(store + " " + item)); // 詳細
                    submitter.addField("acount", account); // 入出金対象
                    submitter.addField("card", ""); // カード決済
                    submitter.addField("inout", "out"); // 出金
                    submitter.addField("amount", payment); // 入出金額
                    submitter.addField("page", "paymentLogic"); // ページ
                    if (sent) {
                        submitter.addField("sent", "checked"); // 送金済み
                    }
                    submitter.submitForm();

                    // UIスレッドでトーストを表示
                    runOnUiThread(() -> {
                        Toast.makeText(ConfirmActivity.this, "送信しました: " + store, Toast.LENGTH_SHORT).show();
                        finish(); // アクティビティを閉じる
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                    runOnUiThread(() -> {
                        Toast.makeText(ConfirmActivity.this, "送信に失敗しました", Toast.LENGTH_SHORT).show();
                    });
                }
            }).start();
        }
    }

    public static byte[] encodeToEucJp(String input) {
        try {
            return input.getBytes("EUC-JP");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return input.getBytes(); // エンコードに失敗した場合は元の文字列を返す
        }
    }
}

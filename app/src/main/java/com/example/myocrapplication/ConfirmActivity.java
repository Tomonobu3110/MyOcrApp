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
import java.util.Date;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

public class ConfirmActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_confirm);

        EditText editTextStore = findViewById(R.id.editTextStore);
        EditText editTextDate = findViewById(R.id.editTextDate);
        EditText editTextPayment = findViewById(R.id.editTextPayment);
        EditText editTextItem = findViewById(R.id.editTextItem);
        Button buttonSubmit = findViewById(R.id.buttonSubmit);
        Button buttonCancel = findViewById(R.id.buttonCancel);

        // 送信ボタンのクリックリスナー
        buttonSubmit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String store = editTextStore.getText().toString();
                String date_in = editTextDate.getText().toString();
                String payment = editTextPayment.getText().toString();
                String item = editTextItem.getText().toString();

                SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy年MM月dd日");
                SimpleDateFormat outputFormat = new SimpleDateFormat("yyyy/MM/dd");
                String date_out;
                try {
                    // 入力文字列を Date に変換
                    Date date_org = inputFormat.parse(date_in);
                    // Date を出力フォーマットの文字列に変換
                    date_out = outputFormat.format(date_org);
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
                    String cgiUrl = preferences.getString("CgiUrl", "");

                    if (cgiUrl.isEmpty()) {
                        Toast.makeText(ConfirmActivity.this, "CGI URL が設定されていません", Toast.LENGTH_SHORT).show();
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
                            submitter.addField("acount", "private"); // 入出金対象
                            submitter.addField("card", "checked"); // カード決済
                            submitter.addField("inout", "out"); // 出金
                            submitter.addField("amount", payment); // 入出金額
                            submitter.addField("page", "paymentLogic"); // ページ
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
        });

        // キャンセルボタンのクリックリスナー
        buttonCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish(); // アクティビティを閉じる
            }
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

    public static byte[] encodeToEucJp(String input) {
        try {
            return input.getBytes("EUC-JP");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return input.getBytes(); // エンコードに失敗した場合は元の文字列を返す
        }
    }
}

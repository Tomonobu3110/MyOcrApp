package com.example.myocrapplication;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import androidx.appcompat.app.AlertDialog;
import android.content.DialogInterface;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    private static final int REQUEST_CAMERA_PERMISSION = 1;

    private PreviewView viewFinder; // PreviewViewの参照
    private Button captureButton;
    private Button settingButton;
    private TextView textView;
    String outputDirectory;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // コンポーネントの取得
        viewFinder    = findViewById(R.id.viewFinder);
        //textView      = findViewById(R.id.textView);
        captureButton = findViewById(R.id.captureButton);
        settingButton = findViewById(R.id.buttonOpenSettings);

        // 設定画面を開く
        settingButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SettingActivity.class);
            startActivity(intent);
        });

        Button buttonPersonal = findViewById(R.id.buttonPersonal);
        Button buttonHousehold = findViewById(R.id.buttonHousehold);

        // SharedPreferences から URL を取得
        SharedPreferences preferences = getSharedPreferences("AppSettings", MODE_PRIVATE);
        String cgiUrl = preferences.getString("CgiUrl", "");
        String cgi2Url = preferences.getString("Cgi2Url", "");

        // 個人ボタンのクリックリスナー
        buttonPersonal.setOnClickListener(v -> openBrowser(cgiUrl));

        // 家計ボタンのクリックリスナー
        buttonHousehold.setOnClickListener(v -> openBrowser(cgi2Url));

        // ファイルを作るディレクトリ
        outputDirectory = getExternalFilesDir(Environment.DIRECTORY_PICTURES).getAbsolutePath();

        // 初期化を権限が付与されているかで判断
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            initializeCamera();
        } else {
            // カメラの権限をリクエスト
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        }
    }

    // ブラウザで URL を開く
    private void openBrowser(String url) {
        if (url != null && !url.isEmpty()) {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            Intent chooser = Intent.createChooser(intent, "ブラウザを選択");
            startActivity(chooser);
        } else {
            // URL が空の場合の処理（例: エラーメッセージを表示
            Toast.makeText(this, "URLが設定されていません", Toast.LENGTH_SHORT).show();
        }
    }

    // 権限が許可された後にカメラを初期化するメソッド
    private void initializeCamera() {
        ProcessCameraProvider.getInstance(this).addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = ProcessCameraProvider.getInstance(this).get();
                Preview preview = new Preview.Builder().build();
                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                preview.setSurfaceProvider(viewFinder.getSurfaceProvider());

                ImageCapture imageCapture = new ImageCapture.Builder().build();

                cameraProvider.bindToLifecycle(
                        this, cameraSelector, preview, imageCapture
                );

                captureButton.setOnClickListener(v -> {
                    File photoFile = new File(
                            outputDirectory,
                            System.currentTimeMillis() + ".jpg"
                    );

                    ImageCapture.OutputFileOptions outputOptions =
                            new ImageCapture.OutputFileOptions.Builder(photoFile).build();

                    imageCapture.takePicture(
                            outputOptions,
                            ContextCompat.getMainExecutor(this),
                            new ImageCapture.OnImageSavedCallback() {
                                @Override
                                public void onError(ImageCaptureException exception) {
                                    Log.e("CameraX", "写真撮影エラー", exception);
                                }

                                @Override
                                public void onImageSaved(ImageCapture.OutputFileResults outputFileResults) {
                                    processImage(photoFile); // OCR処理を呼び出し
                                }
                            }
                    );
                });
            } catch (ExecutionException | InterruptedException e) {
                Log.e("CameraX", "カメラの初期化に失敗しました", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    // 権限リクエストの結果を受け取るメソッド
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 権限が許可された場合はカメラを初期化
                initializeCamera();
            } else {
                // 権限が拒否された場合はエラーメッセージを表示
                Toast.makeText(this, "カメラ権限が必要です", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void processImage(File photoFile) {
        try {
            InputImage image = InputImage.fromFilePath(this, Uri.fromFile(photoFile));
            //TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
            TextRecognizer recognizer = TextRecognition.getClient(new JapaneseTextRecognizerOptions.Builder().build());
            recognizer.process(image)
                    .addOnSuccessListener(visionText -> {
                        // OCR結果を取得
                        String ocrResult = visionText.getText();

                        // OCR結果をダイアログで表示
                        new AlertDialog.Builder(MainActivity.this)
                                .setTitle("OCR結果")
                                .setMessage(ocrResult)
                                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        invokeLambdaFunction(ocrResult);
                                        dialog.dismiss();
                                    }
                                })
                                .show();
                    })
                    .addOnFailureListener(e -> {
                        Log.e("OCR", "OCRエラー", e);
                    });
        } catch (IOException e) {
            Log.e("OCR", "画像ファイルの読み込みエラー", e);
        }
    }
    private void invokeLambdaFunction(String data) {
        OkHttpClient client = new OkHttpClient();

        // JSONとして文字列を送信
        MediaType JSON = MediaType.get("application/json; charset=utf-8");
        String jsonBody = "{\"message\": \"" + data + "\"}";
        Log.i(TAG, jsonBody);
        RequestBody body = RequestBody.create(jsonBody, JSON);

        SharedPreferences preferences = getSharedPreferences("AppSettings", MODE_PRIVATE);
        String lambdaUrl = preferences.getString("LambdaUrl", ""); // デフォルトは空文字列
        //String cgiUrl = preferences.getString("CgiUrl", "");

        Request request = new Request.Builder()
                .url(lambdaUrl)
                .post(body)
                .build();

        // 非同期でリクエストを送信
        new Thread(() -> {
            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    String response_string = response.body().string();
                    Log.i(TAG, "Lambda Response: " + response_string);
                    //showMessageDialog("Lambda Response", response_string);

                    // Show confirm activity(form) to send data to cloud
                    Intent intent = new Intent(MainActivity.this, ConfirmActivity.class);
                    intent.putExtra("response_json", response_string);
                    startActivity(intent);
                } else {
                    Log.e(TAG, "Lambda Call Failed: " + response.code());
                    showMessageDialog("Server Error", "解析に失敗しました");
                }
            } catch (IOException e) {
                Log.e(TAG, "IOException: " + e.getMessage());
            }
        }).start();
    }

    private void showMessageDialog(String title, String message) {
        // runOnUiThread を使ってメインスレッドでダイアログを表示
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setTitle(title);
                builder.setMessage(message);

                builder.setPositiveButton("はい", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // OKボタンがクリックされたときの処理
                        dialog.dismiss();
                    }
                });

                builder.setNegativeButton("いいえ", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // キャンセルボタンがクリックされたときの処理
                        dialog.dismiss();
                    }
                });

                builder.show();
            }
        });
    }
}

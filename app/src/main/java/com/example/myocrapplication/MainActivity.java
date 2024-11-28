package com.example.myocrapplication;

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

import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_CAMERA_PERMISSION = 1;

    private PreviewView viewFinder; // PreviewViewの参照
    private Button captureButton;
    private TextView textView;
    String outputDirectory;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textView      = findViewById(R.id.textView);
        captureButton = findViewById(R.id.captureButton);
        viewFinder    = findViewById(R.id.viewFinder);

        outputDirectory = getExternalFilesDir(Environment.DIRECTORY_PICTURES).getAbsolutePath();

        // CameraXの初期化
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            // CameraXの初期化
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

        } else {
            // カメラの権限をリクエスト
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        }
    }

    private void processImage(File photoFile) {
        try {
            InputImage image = InputImage.fromFilePath(this, Uri.fromFile(photoFile));
            //TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
            TextRecognizer recognizer = TextRecognition.getClient(new JapaneseTextRecognizerOptions.Builder().build());
            recognizer.process(image)
                    .addOnSuccessListener(visionText -> {
                        // OCR結果をTextViewに設定
                        String ocrResult = visionText.getText();
                        // OCR結果を表示
                        textView.setText(ocrResult);
                        // 同じ内容をダイアログで表示
                        new AlertDialog.Builder(MainActivity.this)
                                .setTitle("OCR結果")
                                .setMessage(ocrResult)
                                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
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

}
package com.example.myocrapplication;

import java.io.DataOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class FormSubmitter {
    private final String urlString;
    private final Map<String, byte[]> fields;

    public FormSubmitter(String urlString) {
        this.urlString = urlString;
        this.fields = new HashMap<>();
    }

    public void addField(String name, String value) {
        fields.put(name, value.getBytes());
    }

    public void addField(String name, byte[] value) {
        fields.put(name, value);
    }

    public void submitForm() throws Exception {
        String boundary = UUID.randomUUID().toString();
        String lineEnd = "\r\n";
        String twoHyphens = "--";

        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setDoInput(true);
        connection.setDoOutput(true);
        connection.setUseCaches(false);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + boundary);

        try (DataOutputStream outputStream = new DataOutputStream(connection.getOutputStream())) {
            for (Map.Entry<String, byte[]> entry : fields.entrySet()) {
                outputStream.writeBytes(twoHyphens + boundary + lineEnd);
                outputStream.writeBytes("Content-Disposition: form-data; name=\"" + entry.getKey() + "\"" + lineEnd);
                outputStream.writeBytes(lineEnd);
                outputStream.write(entry.getValue());
                outputStream.writeBytes(lineEnd);
            }

            outputStream.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);
            outputStream.flush();
        }

        int responseCode = connection.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new RuntimeException("サーバーエラー: " + responseCode);
        }
    }
}


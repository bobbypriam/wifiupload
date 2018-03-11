package com.bobbypriambodo.wifiupload;

import android.Manifest;
import android.content.pm.PackageManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.format.Formatter;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import fi.iki.elonen.NanoHTTPD;

public class MainActivity extends AppCompatActivity {
    private Server server;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (server == null) {
            server = new Server(new Random());
        }

        try {
            server.start();
        } catch (IOException e) {
            Log.e("SERVER_START", "Failed to start server.", e);
            renderErrorText();
            return;
        }

        Log.i("SERVER_START", "Server started successfully.");
        renderIpAddress();
    }

    private void renderErrorText() {
        TextView textView = findViewById(R.id.text);
        textView.setText("Unable to start server.");
    }

    private void renderIpAddress() {
        WifiManager wifiManager =
                (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        String ipAddress = Formatter.formatIpAddress(wifiInfo.getIpAddress());

        TextView textView = findViewById(R.id.text);
        textView.setText("http://" + ipAddress + ":" + server.getListeningPort());
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (server != null) {
            server.stop();
            server = null;
        }
    }

    private class FileManager {
        public boolean ensureDirectoryExists() {
            String externalStorageState = Environment.getExternalStorageState();

            if (!Environment.MEDIA_MOUNTED.equals(externalStorageState)) {
                Log.e("WifiUpload", "[FileManager] External storage is not mounted");
                return false;
            }

            if (ContextCompat.checkSelfPermission(getApplicationContext(),
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(MainActivity.this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        0);
            }

            File uploadDirectory = getUploadDirectory();

            if (!uploadDirectory.exists() && !uploadDirectory.mkdirs()) {
                Log.e("WifiUpload", "[FileManager] Failed to create directory");
                return false;
            }

            return true;
        }

        private File getUploadDirectory() {
            File uploadDirectory = new File(getSDCardDirectory(), "WifiUpload");
            return uploadDirectory;
        }

        public void saveFile(String sourcePath, String filename) throws IOException {
            File source = new File(sourcePath);
            File destination = new File(getUploadDirectory(), filename);
            FileUtils.moveFile(source, destination);
        }

        private File getSDCardDirectory() {
            File fallback = Environment.getExternalStorageDirectory();

            for (File f : ContextCompat.getExternalFilesDirs(getApplicationContext(), null)) {
                if (Environment.isExternalStorageRemovable(f)) {
                    return f;
                }
            }

            return fallback;
        }
    }

    private class Server extends NanoHTTPD {
        private static final int LOWEST_PORT = 1205;

        private Server(Random random) {
            super(LOWEST_PORT + random.nextInt(10000 - LOWEST_PORT));
        }

        @Override
        public Response serve(IHTTPSession session) {
            Method method = session.getMethod();
            if (Method.POST.equals(method)) {
                Log.i("SERVE", "Got post method.");

                FileManager fileManager = new FileManager();
                if (!fileManager.ensureDirectoryExists()) {
                    showToast("Failed to ensure directory exists.");
                } else {
                    Map<String, String> files = new HashMap<>();
                    try {
                        session.parseBody(files);
                    } catch (Exception e) {
                        Log.e("WifiUpload", "Error on parsing body");
                    }

                    Map<String, List<String>> parameters = session.getParameters();
                    String filename = parameters.get("file").get(0);
                    String tempFilePath = files.get("file");

                    if (filename == null || tempFilePath == null) {
                        Log.e("WifiUpload", "[FileManager] Empty file");
                    } else {
                        try {
                            fileManager.saveFile(tempFilePath, filename);
                            showToast(filename + " successfully uploaded!");
                        } catch (IOException e) {
                            Log.e("WifiUpload", "Error on copying file", e);
                        }
                    }
                }

                Response res = newFixedLengthResponse(Response.Status.REDIRECT,
                        NanoHTTPD.MIME_HTML, "");
                res.addHeader("Location", "/");
                return res;
            }
            String uploadPage = "<!DOCTYPE html>"
                    + "<meta charset='utf-8'>"
                    + "<form method='post' action='' enctype='multipart/form-data'>"
                    + "<input type='file' name='file' />"
                    + "<input type='submit' name='submit' value='Upload' />"
                    + "</form>";
            return newFixedLengthResponse(uploadPage);
        }

        private void showToast(final String message) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
                }
            });
        }
    }

}

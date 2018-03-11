package com.bobbypriambodo.wifiupload;

import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.format.Formatter;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
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
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(), "Post!", Toast.LENGTH_LONG).show();
                    }
                });
                Response res = newFixedLengthResponse(Response.Status.REDIRECT, NanoHTTPD.MIME_HTML, "");
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
    }

}

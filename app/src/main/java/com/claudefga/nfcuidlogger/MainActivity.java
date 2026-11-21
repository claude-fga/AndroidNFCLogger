package com.claudefga.nfcuidlogger;

import android.app.Activity;
import android.content.Intent;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Bundle;
import android.widget.TextView;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Liest bei geöffneter App die UID jeder NFC-Karte, hängt sie ans Logfile an
 * und lädt das komplette Logfile danach zu Nextcloud hoch (falls eingerichtet).
 */
public class MainActivity extends Activity implements NfcAdapter.ReaderCallback {

    private NfcAdapter nfc;
    private LogStore store;
    private TextView statusView;
    private TextView uploadView;
    private TextView logView;
    private final ExecutorService uploadExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusView = findViewById(R.id.status_text);
        uploadView = findViewById(R.id.upload_text);
        logView = findViewById(R.id.log_text);

        store = new LogStore(this);
        ((TextView) findViewById(R.id.path_text))
                .setText(getString(R.string.log_path, store.file().getAbsolutePath()));

        findViewById(R.id.upload_button).setOnClickListener(v -> uploadAsync());
        findViewById(R.id.settings_button).setOnClickListener(
                v -> startActivity(new Intent(this, SettingsActivity.class)));

        nfc = NfcAdapter.getDefaultAdapter(this);
        refreshLog();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (nfc == null) {
            statusView.setText(R.string.no_nfc);
            return;
        }
        if (!nfc.isEnabled()) {
            statusView.setText(R.string.nfc_off);
        }
        // Reader-Mode: Die App bekommt alle Karten im Vordergrund, ohne dass
        // ein anderes Programm dazwischenfunkt.
        nfc.enableReaderMode(this, this,
                NfcAdapter.FLAG_READER_NFC_A
                        | NfcAdapter.FLAG_READER_NFC_B
                        | NfcAdapter.FLAG_READER_NFC_F
                        | NfcAdapter.FLAG_READER_NFC_V,
                null);
        refreshLog();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (nfc != null) nfc.disableReaderMode(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        uploadExecutor.shutdown();
    }

    /** Läuft auf einem Hintergrund-Thread. */
    @Override
    public void onTagDiscovered(Tag tag) {
        final String uid = toHex(tag.getId());
        final String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.GERMANY).format(new Date());
        try {
            store.append(time + ";" + uid);
        } catch (IOException e) {
            final String message = e.getMessage() != null ? e.getMessage() : e.toString();
            runOnUiThread(() -> statusView.setText(getString(R.string.write_failed, message)));
            return;
        }
        runOnUiThread(() -> {
            statusView.setText(getString(R.string.read_uid, uid));
            refreshLog();
        });
        uploadAsync();
    }

    private static String toHex(byte[] id) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < id.length; i++) {
            if (i > 0) sb.append(':');
            sb.append(String.format(Locale.ROOT, "%02X", id[i]));
        }
        return sb.toString();
    }

    private void uploadAsync() {
        final NextcloudSettings s = NextcloudSettings.load(this);
        if (!s.isConfigured()) {
            runOnUiThread(() -> uploadView.setText(R.string.upload_off));
            return;
        }
        runOnUiThread(() -> uploadView.setText(R.string.uploading));
        try {
            uploadExecutor.execute(() -> {
                String result;
                try {
                    byte[] data = store.readBytes();
                    NextcloudUploader.upload(data, LogStore.FILE_NAME, s);
                    String now = new SimpleDateFormat("HH:mm:ss", Locale.GERMANY).format(new Date());
                    result = getString(R.string.upload_done, now);
                } catch (Exception e) {
                    String message = e.getMessage() != null ? e.getMessage() : e.toString();
                    result = getString(R.string.upload_failed, message);
                }
                final String text = result;
                runOnUiThread(() -> uploadView.setText(text));
            });
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
            // App wird gerade beendet
        }
    }

    private void refreshLog() {
        String text;
        try {
            String all = new String(store.readBytes(), StandardCharsets.UTF_8).trim();
            if (all.isEmpty()) {
                text = getString(R.string.log_empty);
            } else {
                String[] lines = all.split("\n");
                StringBuilder sb = new StringBuilder();
                for (int i = lines.length - 1; i >= 0; i--) {
                    sb.append(lines[i]).append('\n');
                }
                text = sb.toString();
            }
        } catch (IOException e) {
            text = e.toString();
        }
        logView.setText(text);
    }
}

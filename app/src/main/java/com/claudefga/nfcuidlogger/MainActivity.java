package com.claudefga.nfcuidlogger;

import android.app.Activity;
import android.content.Intent;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Bundle;
import android.widget.TextView;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Ablauf:
 * 1. Warten auf eine NFC-Karte. Die UID wird mit der Referenzliste verglichen
 *    (Platzhalter erlaubt). Gültig = Check-Ton, ungültig = Fehlerton.
 * 2. Nach einer gültigen Karte öffnet sich der QR-Scanner.
 * 3. UID und QR-Inhalt werden zusammen als eine Zeile ins Log geschrieben
 *    (und zu Nextcloud hochgeladen, falls eingerichtet). Danach wartet die
 *    App wieder auf die nächste Karte.
 *
 * Logformat: "yyyy-MM-dd HH:mm:ss;UID;STATUS;QR" mit STATUS = OK oder INVALID
 * (bei INVALID ist QR leer).
 */
public class MainActivity extends Activity implements NfcAdapter.ReaderCallback {

    private static final String STATE_PENDING_UID = "pending_uid";
    private static final String STATUS_OK = "OK";
    private static final String STATUS_INVALID = "INVALID";

    private NfcAdapter nfc;
    private LogStore store;
    private SoundPlayer sounds;
    private TextView statusView;
    private TextView uploadView;
    private TextView logView;
    private final ExecutorService uploadExecutor = Executors.newSingleThreadExecutor();

    /** Gesetzt, solange nach einer gültigen Karte auf den QR-Code gewartet wird. */
    private volatile String pendingUid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (savedInstanceState != null) {
            pendingUid = savedInstanceState.getString(STATE_PENDING_UID);
        }

        statusView = findViewById(R.id.status_text);
        uploadView = findViewById(R.id.upload_text);
        logView = findViewById(R.id.log_text);

        store = new LogStore(this);
        sounds = new SoundPlayer();
        ((TextView) findViewById(R.id.path_text))
                .setText(getString(R.string.log_path, store.file().getAbsolutePath()));

        findViewById(R.id.upload_button).setOnClickListener(v -> uploadAsync());
        findViewById(R.id.settings_button).setOnClickListener(
                v -> startActivity(new Intent(this, SettingsActivity.class)));
        findViewById(R.id.reference_button).setOnClickListener(
                v -> startActivity(new Intent(this, ReferenceListActivity.class)));

        nfc = NfcAdapter.getDefaultAdapter(this);
        refreshLog();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_PENDING_UID, pendingUid);
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
        sounds.release();
    }

    /** Läuft auf einem Hintergrund-Thread. */
    @Override
    public void onTagDiscovered(Tag tag) {
        // Während auf den QR-Code gewartet wird, werden weitere Karten ignoriert.
        if (pendingUid != null) return;

        byte[] tagId = tag.getId();
        if (tagId == null) {
            runOnUiThread(() -> statusView.setText(R.string.uid_unavailable));
            sounds.fail();
            return;
        }
        final String uid = toHex(tagId);

        if (!ReferenceList.isValid(this, uid)) {
            sounds.fail();
            try {
                store.append(buildLine(now(), uid, STATUS_INVALID, ""));
            } catch (IOException e) {
                showWriteError(e);
                return;
            }
            runOnUiThread(() -> {
                statusView.setText(getString(R.string.uid_invalid, uid));
                refreshLog();
            });
            uploadAsync();
            return;
        }

        sounds.ok();
        pendingUid = uid;
        runOnUiThread(() -> {
            statusView.setText(getString(R.string.uid_valid, uid));
            startQrScan();
        });
    }

    private void startQrScan() {
        new IntentIntegrator(this)
                .setCaptureActivity(PortraitCaptureActivity.class)
                .setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
                .setPrompt(getString(R.string.scan_qr_prompt))
                .setBeepEnabled(false)
                .setOrientationLocked(false)
                .initiateScan();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result == null) {
            super.onActivityResult(requestCode, resultCode, data);
            return;
        }

        final String uid = pendingUid;
        pendingUid = null;
        if (uid == null) return;

        final String qr = result.getContents();
        if (qr == null) {
            statusView.setText(R.string.qr_cancelled);
            return;
        }

        try {
            store.append(buildLine(now(), uid, STATUS_OK, qr));
        } catch (IOException e) {
            showWriteError(e);
            return;
        }
        statusView.setText(getString(R.string.logged, uid, qr));
        refreshLog();
        uploadAsync();
    }

    private void showWriteError(IOException e) {
        final String message = e.getMessage() != null ? e.getMessage() : e.toString();
        runOnUiThread(() -> statusView.setText(getString(R.string.write_failed, message)));
    }

    private static String now() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.GERMANY).format(new Date());
    }

    private static String buildLine(String time, String uid, String status, String qr) {
        return time + ";" + uid + ";" + status + ";" + csv(qr);
    }

    /** Zeilenumbrüche werden zu Leerzeichen, Semikolon/Anführungszeichen werden CSV-konform maskiert. */
    private static String csv(String value) {
        String s = value.replace("\r", " ").replace("\n", " ");
        if (s.contains(";") || s.contains("\"")) {
            s = "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
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

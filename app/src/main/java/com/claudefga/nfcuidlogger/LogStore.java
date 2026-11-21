package com.claudefga.nfcuidlogger;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Logfile mit einer Zeile pro Scan: "yyyy-MM-dd HH:mm:ss;UID".
 * Liegt im app-spezifischen externen Speicher (Android/data/&lt;Paket&gt;/files),
 * dafür ist keine Speicherberechtigung nötig.
 */
final class LogStore {

    static final String FILE_NAME = "nfc_log.csv";

    private final File file;
    private final Object lock = new Object();

    LogStore(Context context) {
        File dir = context.getExternalFilesDir(null);
        if (dir == null) dir = context.getFilesDir();
        file = new File(dir, FILE_NAME);
    }

    File file() {
        return file;
    }

    void append(String line) throws IOException {
        synchronized (lock) {
            try (FileOutputStream out = new FileOutputStream(file, true)) {
                out.write((line + "\n").getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    byte[] readBytes() throws IOException {
        synchronized (lock) {
            if (!file.exists()) return new byte[0];
            try (FileInputStream in = new FileInputStream(file)) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                return out.toByteArray();
            }
        }
    }
}

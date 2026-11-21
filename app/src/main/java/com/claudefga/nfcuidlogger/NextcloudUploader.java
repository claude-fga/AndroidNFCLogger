package com.claudefga.nfcuidlogger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import okhttp3.Credentials;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/** Minimaler Nextcloud-(WebDAV-)Client: legt den Zielordner an und lädt eine Datei hoch. */
final class NextcloudUploader {

    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();

    private NextcloudUploader() {
    }

    /** Prüft den Login und stellt sicher, dass der Zielordner existiert. */
    static void testConnection(NextcloudSettings s) throws IOException {
        Request request = base(s, userRoot(s))
                .header("Depth", "0")
                .method("PROPFIND", null)
                .build();
        try (Response response = CLIENT.newCall(request).execute()) {
            check(response);
        }
        ensureFolder(s);
    }

    /** Lädt die Daten als Datei in den konfigurierten Ordner hoch (überschreibt eine vorhandene Datei). */
    static void upload(byte[] data, String name, NextcloudSettings s) throws IOException {
        HttpUrl folder = ensureFolder(s);
        HttpUrl target = folder.newBuilder().addPathSegment(name).build();
        RequestBody body = RequestBody.create(data, MediaType.parse("text/csv; charset=utf-8"));
        Request request = base(s, target).put(body).build();
        try (Response response = CLIENT.newCall(request).execute()) {
            check(response);
        }
    }

    private static Request.Builder base(NextcloudSettings s, HttpUrl url) {
        return new Request.Builder()
                .url(url)
                .header("Authorization", Credentials.basic(s.user, s.password, StandardCharsets.UTF_8));
    }

    private static HttpUrl userRoot(NextcloudSettings s) throws IOException {
        HttpUrl server = HttpUrl.parse(s.server);
        if (server == null) throw new IOException("Ungültige Serveradresse");
        return server.newBuilder()
                .addPathSegments("remote.php/dav/files")
                .addPathSegment(s.user)
                .build();
    }

    /** Legt jede Ordnerebene an (MKCOL) und liefert die URL des Zielordners. */
    private static HttpUrl ensureFolder(NextcloudSettings s) throws IOException {
        HttpUrl url = userRoot(s);
        for (String part : s.folder.split("/")) {
            String segment = part.trim();
            if (segment.isEmpty()) continue;
            url = url.newBuilder().addPathSegment(segment).build();
            Request request = base(s, url).method("MKCOL", null).build();
            try (Response response = CLIENT.newCall(request).execute()) {
                if (response.code() != 405) { // 405 = Ordner existiert bereits
                    check(response);
                }
            }
        }
        return url;
    }

    private static void check(Response response) throws IOException {
        if (response.isSuccessful()) return;
        int code = response.code();
        switch (code) {
            case 401:
                throw new IOException("Login fehlgeschlagen - Benutzername und App-Passwort prüfen");
            case 403:
                throw new IOException("Zugriff vom Server verweigert");
            case 404:
                throw new IOException("Nicht gefunden - Serveradresse und Benutzername prüfen");
            case 507:
                throw new IOException("Nextcloud-Speicher ist voll");
            default:
                throw new IOException("Server antwortete mit HTTP " + code);
        }
    }
}

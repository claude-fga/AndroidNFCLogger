# NFC UID Logger

Kleine Android-App: Sobald die App offen ist und eine NFC-Karte ans Handy gehalten wird, wird deren **UID** ausgelesen und mit Zeitstempel in ein Logfile geschrieben. Danach wird das Logfile automatisch zu **Nextcloud** hochgeladen (WebDAV, wie bei audiorec).

## Logfile

- Zeilenformat: `2026-11-21 09:23:45;04:A3:2B:1C` (CSV, Semikolon)
- Ort auf dem Handy: `Android/data/com.claudefga.nfcuidlogger/files/nfc_log.csv` (keine Speicherberechtigung nötig)
- Nextcloud: `<Ordner>/nfc_log.csv`. Nach jedem Scan wird die komplette Datei hochgeladen und die vorhandene überschrieben. Schlägt der Upload fehl (z. B. offline), bleibt das Log lokal erhalten und wird beim nächsten erfolgreichen Upload nachgeholt.

## Nextcloud einrichten

In der App „Nextcloud-Einstellungen“ öffnen und Serveradresse (https), Benutzername, **App-Passwort** (Nextcloud → Einstellungen → Sicherheit) und Zielordner eintragen. „Verbindung testen“ legt den Ordner an.

## APK

Der Build läuft über GitHub Actions (`.github/workflows/build.yml`). Bei jedem Push auf `main` entsteht ein Release mit `nfc-uid-logger.apk` unter *Releases*.

## Hinweise

- Gelesen wird nur bei geöffneter App im Vordergrund.
- Manche Karten (z. B. Kreditkarten, Handys im Kartenmodus) senden bei jedem Lesen eine zufällige UID. Bei Zutrittskarten, MIFARE und NTAG ist sie fest.
- Minimum: Android 7.0 (API 24).

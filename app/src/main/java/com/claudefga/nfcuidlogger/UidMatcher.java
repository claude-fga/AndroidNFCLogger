package com.claudefga.nfcuidlogger;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Vergleicht eine UID mit Mustern der Referenzliste.
 *
 * Ein Muster pro Zeile. Erlaubt sind Hex-Ziffern, die Platzhalter
 * {@code *} (beliebig viele Zeichen) und {@code ?} (genau eine Hex-Ziffer)
 * sowie die Trennzeichen {@code : - } und Leerzeichen, die ignoriert werden.
 * Groß-/Kleinschreibung ist egal. Zeilen mit {@code #} am Anfang oder Ende
 * sind Kommentare, Zeilen mit anderen Zeichen werden übersprungen.
 *
 * Beispiele: "04:A1:B2:C3" (genau diese UID), "04:A1:*" (alle UIDs mit diesem
 * Anfang), "04????C3" (Länge fest, Mitte beliebig), "*" (alles).
 */
final class UidMatcher {

    private static final Pattern ALLOWED_LINE = Pattern.compile("[0-9A-Fa-f*?:\\- ]+");

    private UidMatcher() {
    }

    /** Alle gültigen Muster der Liste, normalisiert (nur Hex und Platzhalter, groß). */
    static List<String> parse(String list) {
        List<String> patterns = new ArrayList<>();
        if (list == null) return patterns;
        for (String line : list.split("\\r?\\n")) {
            String t = line;
            int hash = t.indexOf('#');
            if (hash >= 0) t = t.substring(0, hash);
            t = t.trim();
            if (t.isEmpty() || !ALLOWED_LINE.matcher(t).matches()) continue;
            String normalized = t.toUpperCase(Locale.ROOT).replaceAll("[^0-9A-F*?]", "");
            if (!normalized.isEmpty()) patterns.add(normalized);
        }
        return patterns;
    }

    static boolean matches(String uid, String normalizedPattern) {
        String u = uid.toUpperCase(Locale.ROOT).replaceAll("[^0-9A-F]", "");
        StringBuilder regex = new StringBuilder();
        for (int i = 0; i < normalizedPattern.length(); i++) {
            char c = normalizedPattern.charAt(i);
            if (c == '*') regex.append(".*");
            else if (c == '?') regex.append('.');
            else regex.append(c);
        }
        return Pattern.matches(regex.toString(), u);
    }

    static boolean matchesAny(String uid, String list) {
        for (String pattern : parse(list)) {
            if (matches(uid, pattern)) return true;
        }
        return false;
    }
}

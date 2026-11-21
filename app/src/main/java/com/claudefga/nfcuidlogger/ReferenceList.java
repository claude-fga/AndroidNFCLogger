package com.claudefga.nfcuidlogger;

import android.content.Context;
import android.content.SharedPreferences;

/** Speichert die Referenzliste (Text, ein Muster pro Zeile) in den App-Einstellungen. */
final class ReferenceList {

    private static final String PREFS = "reference_list";
    private static final String KEY_TEXT = "text";

    private ReferenceList() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static String load(Context context) {
        return prefs(context).getString(KEY_TEXT, "");
    }

    static void save(Context context, String text) {
        prefs(context).edit().putString(KEY_TEXT, text).apply();
    }

    static boolean isValid(Context context, String uid) {
        return UidMatcher.matchesAny(uid, load(context));
    }
}

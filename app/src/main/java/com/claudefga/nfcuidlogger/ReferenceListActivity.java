package com.claudefga.nfcuidlogger;

import android.app.Activity;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;

/** Hier wird die Referenzliste der erlaubten UIDs (mit Platzhaltern) bearbeitet. */
public class ReferenceListActivity extends Activity {

    private EditText listField;
    private TextView statusView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reference);

        listField = findViewById(R.id.reference_field);
        statusView = findViewById(R.id.reference_status);
        listField.setText(ReferenceList.load(this));

        findViewById(R.id.reference_save_button).setOnClickListener(v -> save());
    }

    private void save() {
        String text = listField.getText().toString();
        ReferenceList.save(this, text);
        statusView.setText(getString(R.string.reference_saved, UidMatcher.parse(text).size()));
    }
}

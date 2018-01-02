package net.sheetmuse.sheetmuse;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

public class MIDI extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_midi);
        Intent launchIntent = getPackageManager().getLaunchIntentForPackage("com.midisheetmusic");
        startActivity(launchIntent);

    }
}

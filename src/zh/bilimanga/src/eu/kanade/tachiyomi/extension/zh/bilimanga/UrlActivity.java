package eu.kanade.tachiyomi.extension.zh.bilimanga;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

/**
 * Introducing Kotlin-related code (kotlin-stdlib) may cause ClassNotFoundError on some custom ROMs,
 * so I use Java implementation to ensure the best compatibility.
 */
public class UrlActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            String path = getIntent() != null && getIntent().getData() != null ? getIntent().getData().getPath() : null;
            if (path != null && path.matches("/detail/(\\d+)\\.html")) {
                Intent mainIntent = new Intent("eu.kanade.tachiyomi.SEARCH");
                mainIntent.putExtra("query", path);
                mainIntent.putExtra("filter", getPackageName());
                startActivity(mainIntent);
            }
        } catch (Exception e) {
            Log.v("BiliManga", "UrlActivity: " + e.getMessage());
        }
        finish();
        System.exit(0);
    }
}

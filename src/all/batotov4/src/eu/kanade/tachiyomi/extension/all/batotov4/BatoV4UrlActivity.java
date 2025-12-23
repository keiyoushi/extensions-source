package eu.kanade.tachiyomi.extension.all.batotov4;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import java.util.List;

public class BatoV4UrlActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        List<String> pathSegments = getIntent() != null && getIntent().getData() != null
            ? getIntent().getData().getPathSegments()
            : null;
        String query = fromBatoV4(pathSegments);

        if (query != null) {
            Intent mainIntent = new Intent();
            mainIntent.setAction("eu.kanade.tachiyomi.SEARCH");
            mainIntent.putExtra("query", query);
            mainIntent.putExtra("filter", getPackageName());

            try {
                startActivity(mainIntent);
            } catch (ActivityNotFoundException e) {
                Log.e("BatoV4UrlActivity", e.toString());
            }
        } else {
            Log.e("BatoV4UrlActivity", "Unable to parse URI from intent " + getIntent());
        }

        finish();
        System.exit(0);
    }

    private String fromBatoV4(List<String> pathSegments) {
        if (pathSegments == null || pathSegments.size() < 2) return null;

        String path = pathSegments.get(1);
        if (path == null || path.isEmpty()) return null;

        int index = path.indexOf('-');
        String id = index == -1 ? path : path.substring(0, index);
        if (id.isEmpty()) return null;

        return "ID:" + id;
    }
}

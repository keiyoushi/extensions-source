package eu.kanade.tachiyomi.extension.en.reimanga

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log

class UrlActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val data = intent.data
        if (data != null) {
            val intent = Intent().apply {
                setAction("eu.kanade.tachiyomi.SEARCH")
                putExtra("query", data.toString())
                putExtra("filter", packageName)
            }
            try {
                startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                Log.e("ReiManga", "Unable to launch activity", e)
            }
        }

        finish()
        System.exit(0)
    }
}

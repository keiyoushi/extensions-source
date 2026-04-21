package eu.kanade.tachiyomi.extension.all.ososedki

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import kotlin.system.exitProcess

class UrlActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val data = intent?.data

        if (data != null) {
            val activity = Intent().apply {
                action = "eu.kanade.tachiyomi.SEARCH"
                putExtra("query", data.toString())
                putExtra("filter", packageName)
            }
            try {
                startActivity(activity)
            } catch (e: ActivityNotFoundException) {
                Log.e("Ososedki", e.toString())
            }
        } else {
            Log.e("Ososedki", "Failed to parse URI from intent: $intent")
        }

        finish()
        exitProcess(0)
    }
}

package eu.kanade.tachiyomi.extension.es.leermangaesp

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import kotlin.system.exitProcess

class UrlActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val incomingUrl = intent?.data
        if (incomingUrl != null && incomingUrl.pathSegments.isNotEmpty()) {
            val searchIntent = Intent().apply {
                action = "eu.kanade.tachiyomi.SEARCH"
                putExtra("query", incomingUrl.toString())
                putExtra("filter", packageName)
            }
            try {
                startActivity(searchIntent)
            } catch (e: ActivityNotFoundException) {
                Log.e("UrlActivity", e.toString())
            }
        } else {
            Log.e("UrlActivity", "Could not parse URI from intent $intent")
        }

        finish()
        exitProcess(0)
    }
}

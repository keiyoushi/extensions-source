package eu.kanade.tachiyomi.extension.all.baobua

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import kotlin.system.exitProcess

class UrlActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pathSegments = intent?.data?.pathSegments
        if (pathSegments != null && pathSegments.isNotEmpty()) {
            val mainIntent = Intent().apply {
                action = "eu.kanade.tachiyomi.SEARCH"
                putExtra("query", intent.data.toString())
                putExtra("filter", packageName)
            }

            try {
                startActivity(mainIntent)
            } catch (e: ActivityNotFoundException) {
                Log.e("BaoBua", e.toString())
            }
        } else {
            Log.e("BaoBua", "Could not parse URI from intent $intent")
        }

        finish()
        exitProcess(0)
    }
}

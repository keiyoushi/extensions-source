package eu.kanade.tachiyomi.extension.es.insanosscan

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import kotlin.system.exitProcess

class InsanosScanActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uri = intent?.data
        if (uri != null) {
            val mainIntent = Intent().apply {
                action = "eu.kanade.tachiyomi.SEARCH"
                putExtra("query", uri.toString())
                putExtra("filter", packageName)
            }
            try {
                startActivity(mainIntent)
            } catch (e: ActivityNotFoundException) {
                Log.e("InsanosScan", "Could not start search activity", e)
            }
        } else {
            Log.e("InsanosScan", "Could not parse URI from intent ${intent?.data}")
        }

        finish()
        exitProcess(0)
    }
}

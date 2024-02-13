package eu.kanade.tachiyomi.multisrc.po2scans

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import kotlin.system.exitProcess

class PO2ScansUrlActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pathSegments = intent?.data?.pathSegments
        if (pathSegments != null && pathSegments.size > 1) {
            val slug = pathSegments[1]
            val mainIntent = Intent().apply {
                action = "eu.kanade.tachiyomi.SEARCH"
                putExtra("query", "${PO2Scans.SLUG_SEARCH_PREFIX}$slug")
                putExtra("filter", packageName)
            }

            try {
                startActivity(mainIntent)
            } catch (e: ActivityNotFoundException) {
                Log.e("PO2ScansUrlActivity", "Could not start activity", e)
            }
        } else {
            Log.e("PO2ScansUrlActivity", "could not parse URI from intent $intent")
        }

        finish()
        exitProcess(0)
    }
}

package eu.kanade.tachiyomi.multisrc.peachscan

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import kotlin.system.exitProcess

class PeachScanUrlActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pathSegments = intent?.data?.pathSegments

        if (pathSegments != null && pathSegments.size >= 1) {
            val mainIntent = Intent().apply {
                action = "eu.kanade.tachiyomi.SEARCH"
                putExtra("query", "${slug(pathSegments)}")
                putExtra("filter", packageName)
            }
            try {
                startActivity(mainIntent)
            } catch (e: ActivityNotFoundException) {
                Log.e("PeachScanUrlActivity", e.toString())
            }
        } else {
            Log.e("PeachScanUrlActivity", "could not parse uri from intent $intent")
        }

        finish()
        exitProcess(0)
    }

    private fun slug(pathSegments: MutableList<String>): String? {
        return if (pathSegments.size >= 1) {
            val slug = pathSegments[0]
            "${PeachScan.URL_SEARCH_PREFIX}$slug"
        } else {
            null
        }
    }
}

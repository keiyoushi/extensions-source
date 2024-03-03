package eu.kanade.tachiyomi.extension.pt.huntersscans

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import kotlin.system.exitProcess

class HuntersScansUrlActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pathSegments = intent?.data?.pathSegments

        if (pathSegments != null && pathSegments.size > 2) {
            val intent = Intent().apply {
                action = "eu.kanade.tachiyomi.SEARCH"
                putExtra("query", slug(pathSegments))
                putExtra("filter", packageName)
            }

            try {
                startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                Log.e("HuntersScansUrlActivity", e.toString())
            }
        } else {
            Log.e("HuntersScansUrlActivity", "Could not parse URI from intent $intent")
        }

        finish()
        exitProcess(0)
    }

    private fun slug(pathSegments: List<String>) = "${HuntersScans.slugPrefix}${pathSegments.last()}"
}

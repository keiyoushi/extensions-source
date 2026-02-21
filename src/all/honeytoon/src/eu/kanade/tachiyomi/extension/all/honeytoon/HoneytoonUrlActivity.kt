package eu.kanade.tachiyomi.extension.all.honeytoon

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import kotlin.system.exitProcess

class HoneytoonUrlActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pathSegments = intent?.data?.pathSegments

        if (pathSegments != null && pathSegments.size >= 2) {
            val mainIntent = Intent().apply {
                action = "eu.kanade.tachiyomi.SEARCH"
                putExtra("query", "${getPath(pathSegments)}")
                putExtra("filter", packageName)
            }
            try {
                startActivity(mainIntent)
            } catch (e: ActivityNotFoundException) {
                Log.e("MadaraUrl", e.toString())
            }
        } else {
            Log.e("MadaraUrl", "could not parse uri from intent $intent")
        }

        finish()
        exitProcess(0)
    }

    private fun getPath(pathSegments: MutableList<String>): String =
        "${Honeytoon.URL_SEARCH_PREFIX}${pathSegments[pathSegments.size - 2]}/${pathSegments[pathSegments.size - 1]}"
}

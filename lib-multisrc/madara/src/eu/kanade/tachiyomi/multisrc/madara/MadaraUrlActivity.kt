package eu.kanade.tachiyomi.multisrc.madara

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import kotlin.system.exitProcess

class MadaraUrlActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pathSegments = intent?.data?.pathSegments

        if (pathSegments != null && pathSegments.size >= 2) {
            val mainIntent = Intent().apply {
                action = "eu.kanade.tachiyomi.SEARCH"
                putExtra("query", "${getSLUG(pathSegments)}")
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

    private fun getSLUG(pathSegments: MutableList<String>): String? {
        return if (pathSegments.size >= 2) {
            val slug = pathSegments[1]
            "${Madara.URL_SEARCH_PREFIX}$slug"
        } else {
            null
        }
    }
}

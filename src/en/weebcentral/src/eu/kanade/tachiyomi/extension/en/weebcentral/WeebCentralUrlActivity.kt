package eu.kanade.tachiyomi.extension.en.weebcentral

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import kotlin.system.exitProcess

class WeebCentralUrlActivity : Activity() {

    private val tag = javaClass.simpleName

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pathSegments = intent?.data?.pathSegments

        if (pathSegments != null && pathSegments.size >= 3) {
            val mainIntent = Intent().apply {
                action = "eu.kanade.tachiyomi.SEARCH"
                putExtra("query", getEntry(pathSegments))
                putExtra("filter", packageName)
            }
            try {
                startActivity(mainIntent)
            } catch (e: ActivityNotFoundException) {
                Log.e(tag, e.toString())
            }
        } else {
            Log.e(tag, "could not parse uri from intent $intent")
        }

        finish()
        exitProcess(0)
    }

    private fun getEntry(pathSegments: MutableList<String>): String? {
        val id = pathSegments[1]
        val slug = pathSegments[2]
        return "${WeebCentral.URL_SEARCH_PREFIX}$id/$slug"
    }
}

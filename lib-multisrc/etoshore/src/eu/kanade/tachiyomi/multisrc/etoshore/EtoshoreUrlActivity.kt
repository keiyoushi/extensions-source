package eu.kanade.tachiyomi.multisrc.etoshore

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import kotlin.system.exitProcess

class EtoshoreUrlActivity : Activity() {
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
                Log.e("EtoshoreUrl", e.toString())
            }
        } else {
            Log.e("EtoshoreUrl", "could not parse uri from intent $intent")
        }

        finish()
        exitProcess(0)
    }

    private fun getSLUG(pathSegments: MutableList<String>): String? {
        return if (pathSegments.size >= 2) {
            val slug = pathSegments[1]
            "${Etoshore.PREFIX_SEARCH}$slug"
        } else {
            null
        }
    }
}

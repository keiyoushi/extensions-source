package eu.kanade.tachiyomi.multisrc.heancms

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import kotlin.system.exitProcess

class HeanCmsUrlActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pathSegments = intent?.data?.pathSegments

        if (pathSegments != null && pathSegments.size >= 2) {
            val mainIntent = Intent().apply {
                action = "eu.kanade.tachiyomi.SEARCH"
                putExtra("query", createQuery(pathSegments))
                putExtra("filter", packageName)
            }
            try {
                startActivity(mainIntent)
            } catch (e: ActivityNotFoundException) {
                Log.e("HeanCmsUrlActivity", e.toString())
            }
        } else {
            Log.e("HeanCmsUrlActivity", "could not parse uri from intent $intent")
        }

        finish()
        exitProcess(0)
    }

    private fun createQuery(pathSegments: MutableList<String>): String? {
        return if (pathSegments.size >= 2) {
            val slug = pathSegments[1]
            "${HeanCms.SEARCH_PREFIX}$slug"
        } else {
            null
        }
    }
}

package eu.kanade.tachiyomi.extension.all.xinmeitulu

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import kotlin.system.exitProcess

class XinmeituluUrlActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val host = intent?.data?.host
        val pathSegments = intent?.data?.pathSegments

        if (host != null && pathSegments != null) {
            val query = fromUrl(pathSegments)

            if (query == null) {
                Log.e("XinmeiTuluUrlActivity", "Unable to parse URI from intent $intent")
                finish()
                exitProcess(1)
            }

            val mainIntent = Intent().apply {
                action = "eu.kanade.tachiyomi.SEARCH"
                putExtra("query", query)
                putExtra("filter", packageName)
            }

            try {
                startActivity(mainIntent)
            } catch (e: ActivityNotFoundException) {
                Log.e("XinmeiTuluUrlActivity", e.toString())
            }
        }

        finish()
        exitProcess(0)
    }

    private fun fromUrl(pathSegments: MutableList<String>): String? {
        return if (pathSegments.size >= 2) {
            val slug = pathSegments[1]
            "SLUG:$slug"
        } else {
            null
        }
    }
}

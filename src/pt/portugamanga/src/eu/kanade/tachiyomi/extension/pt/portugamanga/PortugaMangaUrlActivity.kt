package eu.kanade.tachiyomi.extension.pt.portugamanga

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import kotlin.system.exitProcess

class PortugaMangaUrlActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pathSegments = intent?.data?.pathSegments

        if (pathSegments != null && pathSegments.size > 1) {
            val slug = pathSegments[1]
            val intent = Intent().apply {
                action = "eu.kanade.tachiyomi.SEARCH"
                putExtra("filter", packageName)
            }

            try {
                startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                Log.e("PortugaMangaUrlActivity", e.toString())
            }
        } else {
            Log.e("PortugaMangaUrlActivity", "Could not parse URI from intent $intent")
        }

        finish()
        exitProcess(0)
    }
}

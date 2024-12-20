package eu.kanade.tachiyomi.extension.all.deviantart

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import kotlin.system.exitProcess

class DeviantArtUrlActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pathSegments = intent?.data?.pathSegments

        if (pathSegments != null && pathSegments.size >= 3) {
            val username = pathSegments[0]
            val folderId = pathSegments[2]

            val mainIntent = Intent().apply {
                action = "eu.kanade.tachiyomi.SEARCH"
                putExtra("query", "gallery:$username/$folderId")
                putExtra("filter", packageName)
            }

            try {
                startActivity(mainIntent)
            } catch (e: ActivityNotFoundException) {
                Log.e("DeviantArtUrlActivity", e.toString())
            }
        } else {
            Log.e("DeviantArtUrlActivity", "Could not parse URI from intent $intent")
        }

        finish()
        exitProcess(0)
    }
}

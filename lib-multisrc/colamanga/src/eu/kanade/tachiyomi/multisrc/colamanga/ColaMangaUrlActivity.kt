package eu.kanade.tachiyomi.multisrc.colamanga

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import kotlin.system.exitProcess

class ColaMangaUrlActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pathSegments = intent?.data?.pathSegments

        if (pathSegments != null && pathSegments.size > 0) {
            val intent =
                Intent().apply {
                    action = "eu.kanade.tachiyomi.SEARCH"
                    putExtra("query", "${ColaManga.PREFIX_SLUG_SEARCH}${pathSegments[0]}")
                    putExtra("filter", packageName)
                }

            try {
                startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                Log.e("ColaMangaUrlActivity", "Could not start activity", e)
            }
        } else {
            Log.e("ColaMangaUrlActivity", "Could not parse URI from intent $intent")
        }

        finish()
        exitProcess(0)
    }
}

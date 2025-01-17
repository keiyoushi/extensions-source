package eu.kanade.tachiyomi.extension.en.dynasty

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import kotlin.system.exitProcess

class DynastyUrlActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pathSegments = intent?.data?.pathSegments
        if (pathSegments != null && pathSegments.size > 1) {
            val id = pathSegments[1]
            val mainIntent =
                Intent().apply {
                    action = "eu.kanade.tachiyomi.SEARCH"
                    putExtra("query", "manga:${pathSegments[0]}:$id")
                    putExtra("filter", packageName)
                }

            try {
                startActivity(mainIntent)
            } catch (e: ActivityNotFoundException) {
                Log.e("DynastyUrlActivity", e.toString())
            }
        } else {
            Log.e("DynastyUrlActivity", "could not parse uri from intent $intent")
        }

        finish()
        exitProcess(0)
    }
}

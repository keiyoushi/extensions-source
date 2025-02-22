package eu.kanade.tachiyomi.extension.all.simplycosplay

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import kotlin.system.exitProcess

class SimplyCosplayUrlActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pathSegments = intent?.data?.pathSegments
        if (pathSegments != null && pathSegments.size >= 3) {
            val mainIntent = Intent().apply {
                action = "eu.kanade.tachiyomi.SEARCH"
                putExtra("query", "${SimplyCosplay.SEARCH_PREFIX}/${pathSegments[0]}/new/${pathSegments[2]}")
                putExtra("filter", packageName)
            }

            try {
                startActivity(mainIntent)
            } catch (e: ActivityNotFoundException) {
                Log.e("SimplyCosplayUrlActivit", e.toString())
            }
        } else {
            Log.e("SimplyCosplayUrlActivit", "could not parse uri from intent $intent")
        }

        finish()
        exitProcess(0)
    }
}

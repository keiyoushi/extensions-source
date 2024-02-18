package eu.kanade.tachiyomi.extension.all.comickfun

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import kotlin.system.exitProcess

class ComickUrlActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pathSegments = intent?.data?.pathSegments
        if (pathSegments != null && pathSegments.size > 1) {
            val slug = pathSegments[1]
            val mainIntent = Intent().apply {
                action = "eu.kanade.tachiyomi.SEARCH"
                putExtra("query", "${Comick.SLUG_SEARCH_PREFIX}$slug")
                putExtra("filter", packageName)
            }

            try {
                startActivity(mainIntent)
            } catch (e: ActivityNotFoundException) {
                Log.e("ComickFunUrlActivity", e.toString())
            }
        } else {
            Log.e("ComickFunUrlActivity", "could not parse uri from intent $intent")
        }

        finish()
        exitProcess(0)
    }
}

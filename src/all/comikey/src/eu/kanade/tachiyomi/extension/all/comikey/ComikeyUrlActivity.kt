package eu.kanade.tachiyomi.extension.all.comikey

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import kotlin.system.exitProcess

class ComikeyUrlActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pathSegments = intent?.data?.pathSegments

        if (pathSegments != null && pathSegments.size > 2) {
            val intent = Intent().apply {
                action = "eu.kanade.tachiyomi.SEARCH"
                putExtra("query", "${Comikey.PREFIX_SLUG_SEARCH}${pathSegments[1]}/${pathSegments[2]}")
                putExtra("filter", packageName)
            }

            try {
                startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                Log.e("ComikeyUrlActivity", "Could not start activity", e)
            }
        } else {
            Log.e("ComikeyUrlActivity", "Could not parse URI from intent $intent")
        }

        finish()
        exitProcess(0)
    }
}

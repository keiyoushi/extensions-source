package eu.kanade.tachiyomi.extension.es.zonatmoorg

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import kotlin.system.exitProcess

class UrlActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pathSegments = intent?.data?.pathSegments
        if (pathSegments != null && pathSegments.size >= 2 && pathSegments[0] == "manga") {
            val mangaSlug = pathSegments[1]
            val mainIntent = Intent().apply {
                action = "eu.kanade.tachiyomi.SEARCH"
                putExtra("query", "${ZonaTmoOrg.PREFIX_SLUG_SEARCH}$mangaSlug")
                putExtra("filter", packageName)
            }
            try {
                startActivity(mainIntent)
            } catch (e: ActivityNotFoundException) {
                Log.e("ZonaTMO", e.toString())
            }
        } else {
            Log.e("ZonaTMO", "Could not parse URI from intent: ${intent?.data}")
        }

        finish()
        exitProcess(0)
    }
}

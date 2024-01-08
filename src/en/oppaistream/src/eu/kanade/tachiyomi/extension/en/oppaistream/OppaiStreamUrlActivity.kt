package eu.kanade.tachiyomi.extension.en.oppaistream

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import kotlin.system.exitProcess

class OppaiStreamUrlActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent?.data
        val slug = uri?.getQueryParameter("m")
        if (slug != null) {
            val mainIntent = Intent().apply {
                action = "eu.kanade.tachiyomi.SEARCH"
                putExtra("query", "${OppaiStream.SLUG_SEARCH_PREFIX}$slug")
                putExtra("filter", packageName)
            }

            try {
                startActivity(mainIntent)
            } catch (e: ActivityNotFoundException) {
                Log.e("OppaiStreamUrlActivity", e.toString())
            }
        } else {
            Log.e("OppaiStreamUrlActivity", "slug not found in uri $uri")
        }

        finish()
        exitProcess(0)
    }
}

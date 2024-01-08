package eu.kanade.tachiyomi.extension.ja.twi4

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import kotlin.system.exitProcess

class Twi4Activity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pathSegments = intent?.data?.pathSegments
        if (pathSegments != null && pathSegments.size > 2) {
            val slug = pathSegments[2]
            val mainIntent = Intent().apply {
                action = "eu.kanade.tachiyomi.SEARCH"
                putExtra("query", "${Twi4.SEARCH_PREFIX_SLUG}$slug")
                putExtra("filter", packageName)
            }

            startActivity(mainIntent)
        }

        finish()
        exitProcess(0)
    }
}

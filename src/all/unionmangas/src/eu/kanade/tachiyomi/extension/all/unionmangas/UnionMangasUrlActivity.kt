package eu.kanade.tachiyomi.extension.all.unionmangas

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import kotlin.system.exitProcess

class UnionMangasUrlActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val host = intent?.data?.host
        val pathSegments = intent?.data?.pathSegments

        if (host != null && pathSegments != null) {
            val query = null

            if (query == null) {
                Log.e("UnionMangasUrlActivity", "Unable to parse URI from intent $intent")
                finish()
                exitProcess(1)
            }

            val mainIntent = Intent().apply {
                action = "eu.kanade.tachiyomi.SEARCH"
//                putExtra("query", query)
                putExtra("filter", packageName)
            }

            try {
                startActivity(mainIntent)
            } catch (e: ActivityNotFoundException) {
                Log.e("UnionMangasUrlActivity", e.toString())
            }
        }

        finish()
        exitProcess(0)
    }
}

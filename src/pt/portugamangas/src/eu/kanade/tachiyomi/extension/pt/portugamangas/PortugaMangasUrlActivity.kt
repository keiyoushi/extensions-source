package eu.kanade.tachiyomi.extension.pt.portugamangas

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import kotlin.system.exitProcess

class PortugaMangasUrlActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pathSegments = intent?.data?.pathSegments

        if (pathSegments != null && pathSegments.size > 1) {
            val intent = Intent().apply {
                action = "eu.kanade.tachiyomi.SEARCH"
                putExtra("filter", packageName)
            }

            try {
                startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                Log.e("PortugaMangasUrlActivity", e.toString())
            }
        } else {
            Log.e("PortugaMangasUrlActivity", "Could not parse URI from intent $intent")
        }

        finish()
        exitProcess(0)
    }
}

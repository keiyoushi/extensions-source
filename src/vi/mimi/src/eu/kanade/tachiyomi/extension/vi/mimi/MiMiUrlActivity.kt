package eu.kanade.tachiyomi.extension.vi.mimi

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import kotlin.system.exitProcess

class MiMiUrlActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pathSegments = intent?.data?.pathSegments
        if (pathSegments != null && pathSegments.size > 1) {
            val id = pathSegments[1]
            val mainIntent = Intent().apply {
                action = "eu.kanade.tachiyomi.SEARCH"
                putExtra("query", "${MiMi.PREFIX_ID_SEARCH}$id")
                putExtra("filter", packageName)
            }

            try {
                startActivity(mainIntent)
            } catch (e: Exception) {
                Log.e("MiMiUrlActivity", "Error: ${e.message}")
            }
        } else {
            Log.e("MiMiUrlActivity", "Unable to parse URI: ${intent.data}")
        }

        finish()
        exitProcess(0)
    }
}

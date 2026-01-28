package eu.kanade.tachiyomi.extension.vi.teamlanhlung

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import kotlin.system.exitProcess

class TeamLanhLungUrlActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pathSegments = intent?.data?.pathSegments
        if (pathSegments != null && pathSegments.isNotEmpty()) {
            val slug = pathSegments[0]
            try {
                startActivity(
                    Intent().apply {
                        action = "eu.kanade.tachiyomi.SEARCH"
                        putExtra("query", "id:$slug")
                        putExtra("filter", packageName)
                    },
                )
            } catch (e: ActivityNotFoundException) {
                Log.e("TeamLanhLungUrlActivity", e.toString())
            }
        } else {
            Log.e("TeamLanhLungUrlActivity", "Could not parse URI from intent $intent")
        }

        finish()
        exitProcess(0)
    }
}

package eu.kanade.tachiyomi.extension.vi.teamlanhlung

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import kotlin.system.exitProcess

/*
    Springboard that accepts https://<domain>/truyen-tranh/$id/ intents
 */
class TeamLanhLungUrlActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pathSegments = intent?.data?.pathSegments
        if (pathSegments != null && pathSegments.size > 1) {
            val id = pathSegments[1]
            try {
                startActivity(
                    Intent().apply {
                        action = "eu.kanade.tachiyomi.SEARCH"
                        putExtra("query", "id:$id")
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

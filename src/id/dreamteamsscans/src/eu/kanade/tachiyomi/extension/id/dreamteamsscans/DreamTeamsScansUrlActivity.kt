package eu.kanade.tachiyomi.extension.id.dreamteamsscans

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import kotlin.system.exitProcess

class DreamTeamsScansUrlActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pathSegments = intent?.data?.pathSegments
        if (pathSegments != null && pathSegments.size > 1) {
            val id = pathSegments[1]
            try {
                startActivity(
                    Intent().apply {
                        action = "eu.kanade.tachiyomi.SEARCH"
                        putExtra("query", "${DreamTeamsScans.PREFIX_ID_SEARCH}$id")
                        putExtra("filter", packageName)
                    },
                )
            } catch (e: ActivityNotFoundException) {
                Log.e("DreamTeamsScansUrl", e.toString())
            }
        } else {
            Log.e("DreamTeamsScansUrl", "Could not parse URI from intent $intent")
        }

        finish()
        exitProcess(0)
    }
}

package eu.kanade.tachiyomi.extension.all.projectsuki.activities

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import eu.kanade.tachiyomi.extension.all.projectsuki.EXTENSION_INFO
import eu.kanade.tachiyomi.extension.all.projectsuki.SHORT_FORM_ID
import kotlin.system.exitProcess

/**
 *  @see EXTENSION_INFO Found in ProjectSuki.kt
 */
@Suppress("unused")
private inline val INFO: Nothing get() = error("INFO")

internal const val INTENT_BOOK_QUERY_PREFIX: String = """${'$'}$SHORT_FORM_ID-book:"""

/**
 * See [handleIntentAction](https://github.com/tachiyomiorg/tachiyomi/blob/0f9895eec8f5808210f291d1e0ef5cc9f73ccb44/app/src/main/java/eu/kanade/tachiyomi/ui/main/MainActivity.kt#L401)
 * and [GlobalSearchScreen](https://github.com/tachiyomiorg/tachiyomi/blob/0f9895eec8f5808210f291d1e0ef5cc9f73ccb44/app/src/main/java/eu/kanade/tachiyomi/ui/browse/source/globalsearch/GlobalSearchScreen.kt#L19)
 * (these are permalinks, search for updated variants).
 *
 * See [AndroidManifest.xml](https://developer.android.com/guide/topics/manifest/manifest-intro)
 * for what URIs this [Activity](https://developer.android.com/guide/components/activities/intro-activities)
 * can receive.
 *
 * For this specific class you can test the activity by doing (see [CONTRIBUTING](https://github.com/tachiyomiorg/tachiyomi-extensions/blob/master/CONTRIBUTING.md#url-intent-filter)):
 * ```
 * adb shell am start -d "https://projectsuki.com/book/192047" -a android.intent.action.VIEW
 * ```
 *
 * If multiple devices are present, [see this answer](https://stackoverflow.com/a/14655015).
 *
 * Note that Tachiyomi extension's UrlActivities [do not have access to the Kotlin environment](https://github.com/tachiyomiorg/tachiyomi-extensions/pull/19323#issuecomment-1858932113)
 * (specifically Intrinsics).
 * To avoid using Java, multiple classes have been provided for different URLs.
 *
 * @author Federico d'Alonzo &lt;me@npgx.dev&gt;
 */
class ProjectSukiBookUrlActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if ((intent?.data?.pathSegments?.size ?: 0) >= 2) {
            // Project Suki Url Activity Book
            Log.e("PSUABook", "could not handle URI ${intent?.data} from intent $intent")
        }

        val intent = Intent().apply {
            // tell tachiyomi we want to search for something
            action = "eu.kanade.tachiyomi.SEARCH"
            // "filter" for our own extension instead of doing a global search
            putExtra("filter", packageName)
            // value that will be passed onto the "query" parameter in fetchSearchManga
            putExtra("query", "$INTENT_BOOK_QUERY_PREFIX${intent?.data?.pathSegments?.get(1)}")
        }

        try {
            // actually do the thing
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // tachiyomi isn't installed (?)
            // Project Suki Url Activity Book
            Log.e("PSUABook", e.toString())
        }

        // we're done
        finish()
        // just for safety
        exitProcess(0)
    }
}

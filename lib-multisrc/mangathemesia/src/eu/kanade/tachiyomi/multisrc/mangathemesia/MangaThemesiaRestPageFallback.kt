package eu.kanade.tachiyomi.multisrc.mangathemesia

import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Page
import keiyoushi.lib.i18n.Intl
import keiyoushi.utils.parseAs
import kotlinx.serialization.Serializable
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response

/**
 * Locked-chapter bypass for MangaThemesia/MangaReader sites whose
 * "lock chapter" plugin only filters the public chapter HTML but leaves
 * the WordPress REST API exposed. The list page on these sites carries
 * the WP post id for each chapter (in `data-id` / `data-post-id`) — once
 * recovered, `/wp-json/wp/v2/posts/<id>` returns the canonical chapter
 * content with all image URLs intact.
 *
 * Wire-up in an extension:
 *
 *  1. In [chapterFromElement][eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia.chapterFromElement],
 *     after calling super, dig the post id out of the element and call
 *     [encodePostId] to stash it on `chapter.url`. Use [synthesizeUrlForLockedChapter]
 *     when the element has no `<a href>` (sites that strip the link from
 *     locked chapters).
 *  2. Override `pageListRequest` and short-circuit to [restRequest] when
 *     the bypass is enabled and the URL carries an encoded post id.
 *  3. Override `pageListParse(response)` and dispatch to [parseRestResponse]
 *     for JSON responses — fall through to the default HTML parser
 *     otherwise.
 *
 * Sites known to be vulnerable: WitchScans (custom `wp-manga-auth-profiles`
 * plugin) and Drake Scans (Cloudflare-fronted but REST left wide open).
 */
class MangaThemesiaRestPageFallback(
    private val prefKey: String = "pref_unlock_paid_chapters",
    private val defaultEnabled: Boolean = false,
) {

    fun addPreferenceToScreen(screen: PreferenceScreen, intl: Intl) {
        SwitchPreferenceCompat(screen.context).apply {
            key = prefKey
            title = intl["pref_unlock_paid_chapters_title"]
            summary = intl["pref_unlock_paid_chapters_summary"]
            setDefaultValue(defaultEnabled)
        }.also(screen::addPreference)
    }

    fun isEnabled(prefs: SharedPreferences): Boolean = prefs.getBoolean(prefKey, defaultEnabled)

    /** Append `#postId=N` to a chapter URL so the bypass can later recover
     *  the post id without re-parsing the list page. URL fragments are
     *  client-side, so the encoded data does not leak into HTTP requests. */
    fun encodePostId(chapterUrl: String, postId: String): String {
        val cleaned = chapterUrl.substringBefore('#')
        return "$cleaned#$POST_ID_FRAGMENT_KEY=$postId"
    }

    fun decodePostId(chapterUrl: String): String? = POST_ID_FRAGMENT_REGEX.find(chapterUrl)?.groupValues?.get(1)

    /** WitchScans-style sites strip the `<a href>` from locked chapter list
     *  entries. WordPress always resolves `/?p=<id>` to the post (or
     *  redirects to its canonical URL), so it makes a safe stand-in URL —
     *  used for the "open in browser" UI; the bypass itself never touches
     *  the chapter HTML. */
    fun synthesizeUrlForLockedChapter(postId: String): String = "/?p=$postId"

    fun restRequest(baseUrl: String, postId: String, headers: Headers): Request = GET("$baseUrl/wp-json/wp/v2/posts/$postId", headers)

    /** Parse pages from a REST API response fired by [restRequest]. */
    fun parseRestResponse(response: Response, chapterUrl: String): List<Page> {
        val post = try {
            response.parseAs<WpPostDto>()
        } catch (_: Exception) {
            return emptyList()
        }
        val content = post.content?.rendered.orEmpty()
        if (content.isEmpty()) return emptyList()
        return IMG_SRC_REGEX.findAll(content)
            .map { it.groupValues[1] }
            .filter { "/wp-content/uploads/" in it && !SKIP_IMG_REGEX.containsMatchIn(it) }
            .toList()
            .mapIndexed { i, url -> Page(i, url = chapterUrl, imageUrl = url) }
    }

    @Serializable
    private class WpPostDto(val content: WpRenderedDto? = null)

    @Serializable
    private class WpRenderedDto(val rendered: String? = null)

    companion object {
        private const val POST_ID_FRAGMENT_KEY = "wp_post_id"
        private val POST_ID_FRAGMENT_REGEX = Regex("""#${POST_ID_FRAGMENT_KEY}=(\d+)""")
        private val IMG_SRC_REGEX = Regex("""<img[^>]+src=["']([^"']+)["']""")
        private val SKIP_IMG_REGEX = Regex(
            "(logo|icon|cropped|preroll|placeholder|loading|spinner|avatar)",
            RegexOption.IGNORE_CASE,
        )
    }
}

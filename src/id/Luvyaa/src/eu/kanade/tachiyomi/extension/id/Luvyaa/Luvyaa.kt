@file:Suppress("ktlint:standard:package-name")

package eu.kanade.tachiyomi.extension.id.Luvyaa

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.utils.getPreferencesLazy
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document

@Source
abstract class Luvyaa :
    MangaThemesia(),
    ConfigurableSource {
    override val datePattern = "dd/MM/yyyy"

    override val seriesAuthorSelector = metaSelector("Author")
    override val seriesArtistSelector = metaSelector("Artist")
    override val seriesTypeSelector = metaSelector("Type")
    override val seriesGenreSelector = ".mgen a"
    override val seriesStatusSelector = ".kh-status .status-text"

    private val preferences by getPreferencesLazy {
        if (contains(PREF_HIDE_LOCKED_OLD)) edit().remove(PREF_HIDE_LOCKED_OLD).apply()
    }

    // luvyaa.co redirects to the current domain, which answers the lookup with a Cloudflare challenge
    override suspend fun fetchBaseUrls(client: OkHttpClient): List<String> = listOf(
        client.newBuilder().followRedirects(false).build()
            .get(HUB_URL, Fetch.DOCUMENT, ensureSuccess = false)
            .use { it.header("Location")!! },
    )

    // Links from the site's earlier domains
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val base = baseUrl.toHttpUrl()
        if (url.topPrivateDomain() != base.topPrivateDomain()) return null
        return super.getMangaByUrl(url.newBuilder().scheme(base.scheme).host(base.host).build())
    }

    override fun searchMangaUrl(page: Int, query: String) = if (query.isNotEmpty()) {
        baseUrl.toHttpUrl().newBuilder().apply {
            addQueryParameter("s", query)
            addQueryParameter("page", page.toString())
        }
    } else {
        super.searchMangaUrl(page, query)
    }

    override fun chapterListParse(document: Document): List<SChapter> {
        val base = baseUrl.toHttpUrl()
        val lockedPaths = LOCKED_URLS_REGEX.find(document.toString())?.groupValues?.get(1)
            ?.split(",")
            ?.mapNotNull { url ->
                url.trim().removeSurrounding("'").removeSurrounding("\"").replace("\\/", "/")
                    .let(base::resolve)?.encodedPath?.trimEnd('/')
            }
            ?.toSet()
            .orEmpty()

        val showLocked = preferences.getBoolean(PREF_SHOW_LOCKED, false)

        return document.select(super.chapterListSelector()).mapNotNull { element ->
            val chapter = super.chapterFromElement(element)
            val isLocked = chapter.url.trimEnd('/') in lockedPaths

            if (isLocked && !showLocked) return@mapNotNull null

            chapter.apply {
                if (isLocked) {
                    name = "🔒 $name"
                }
            }
        }
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_SHOW_LOCKED
            title = "Tampilkan chapter terkunci"
            summary = "Chapter Early Access khusus member Premium, ditandai 🔒"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    private fun metaSelector(label: String) = ".meta-item:has(> .meta-label:matchesOwn(^$label$)) .meta-pill"

    companion object {
        private const val HUB_URL = "https://luvyaa.co/"
        private const val PREF_SHOW_LOCKED = "pref_show_locked_chapters"
        private const val PREF_HIDE_LOCKED_OLD = "pref_hide_locked_chapters"

        private val LOCKED_URLS_REGEX = """lockedUrls\s*=\s*\[([^\]]*)]""".toRegex()
    }
}

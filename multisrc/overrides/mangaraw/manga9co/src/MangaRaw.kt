package eu.kanade.tachiyomi.extension.ja.manga9co

import android.app.Application
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.multisrc.mangaraw.ImageListParser
import eu.kanade.tachiyomi.multisrc.mangaraw.MangaRawTheme
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Evaluator
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.random.Random

class MangaRaw : MangaRawTheme("MangaRaw", ""), ConfigurableSource {
    // See https://github.com/tachiyomiorg/tachiyomi-extensions/commits/master/src/ja/mangaraw
    override val versionId = 2
    override val id = 4572869149806246133

    private val isCi = System.getenv("CI") == "true"
    override val baseUrl get() = when {
        isCi -> MIRRORS.joinToString("#, ") { "https://$it" }
        else -> _baseUrl
    }

    override val supportsLatest = true
    private val _baseUrl: String
    private val selectors: Selectors
    private val needUrlSanitize: Boolean
    private val isPagesShuffled: Boolean

    init {
        val mirrors = MIRRORS
        val preferences = Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
        var mirrorIndex = preferences.getString(MIRROR_PREF, "-1")!!.toInt()

        if (mirrorIndex !in mirrors.indices) {
            mirrorIndex = Random.nextInt(RANDOM_MIRROR_FROM, RANDOM_MIRROR_UNTIL)
            preferences.edit().putString(MIRROR_PREF, mirrorIndex.toString()).apply()
        }

        _baseUrl = "https://" + mirrors[mirrorIndex]
        selectors = getSelectors(mirrorIndex)
        needUrlSanitize = needUrlSanitize(mirrorIndex)
        isPagesShuffled = isPagesShuffled(mirrorIndex)
    }

    override fun String.sanitizeTitle() = substringBeforeLast('(').trimEnd()

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/top/?page=$page", headers)
    override fun popularMangaSelector() = selectors.listMangaSelector
    override fun popularMangaNextPageSelector() = ".nextpostslink"

    override fun popularMangaFromElement(element: Element) = super.popularMangaFromElement(element).apply {
        if (needUrlSanitize) url = mangaSlugRegex.replaceFirst(url, "/")
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) =
        GET("$baseUrl/?s=$query&page=$page", headers)

    override fun Document.getSanitizedDetails(): Element =
        selectFirst(selectors.detailsSelector)!!.apply {
            val recommendClass = selectors.recommendClass
            children().find { it.hasClass(recommendClass) }?.remove()
            selectFirst(Evaluator.Class("list-scoll"))!!.remove()
        }

    override fun chapterListSelector() = ".list-scoll a"
    override fun String.sanitizeChapter() = substring(lastIndexOf('【') + 1, length - 1)

    override fun pageSelector() = Evaluator.Class("card-wrap")

    override fun pageListParse(response: Response): List<Page> {
        if (!isPagesShuffled) return super.pageListParse(response)
        val html = response.body.string()
        val imageList = ImageListParser(html, 32).getImageList() ?: return emptyList()
        return imageList.mapIndexed { index, imageUrl ->
            Page(index, imageUrl = imageUrl)
        }
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = MIRROR_PREF
            title = "Mirror"
            summary = "%s\n" +
                "Requires app restart to take effect\n" +
                PROMPT
            entries = MIRRORS
            entryValues = MIRRORS.indices.map { it.toString() }.toTypedArray()
            setDefaultValue("0")
        }.let { screen.addPreference(it) }
    }
}

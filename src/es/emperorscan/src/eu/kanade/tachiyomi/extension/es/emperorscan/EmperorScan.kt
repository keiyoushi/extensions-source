package eu.kanade.tachiyomi.extension.es.emperorscan

import android.content.SharedPreferences
import android.widget.Toast
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.lib.randomua.addRandomUAPreference
import keiyoushi.lib.randomua.setRandomUserAgent
import keiyoushi.network.rateLimit
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class EmperorScan :
    Madara(),
    ConfigurableSource {

    override val dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("es"))

    override val useLoadMoreRequest = LoadMoreStrategy.Never

    private val baseUrlHost by lazy { baseUrl.toHttpUrl().host }

    override val client = super.client.newBuilder()
        .rateLimit(2) { it.host == baseUrlHost }
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .setRandomUserAgent()

    override fun getMangaUrl(manga: SManga) = baseUrl + manga.url

    override fun popularMangaSelector() = "div#mkAgrid > a.acard"

    override fun popularMangaNextPageSelector() = "div.wp-pagenavi > a.nextpostslink"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.attr("abs:href"))
        title = element.selectFirst("div.ac-t")!!.ownText()
        element.selectFirst(popularMangaUrlSelectorImg)?.let {
            thumbnail_url = processThumbnail(imageFromElement(it), true)
        }
    }

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    override val mangaDetailsSelectorTitle = "div.hcol > .htitle"
    override val mangaDetailsSelectorStatus = "div.hcol > .htags > .htag--status"
    override val mangaDetailsSelectorDescription = "div#syn > p"
    override val mangaDetailsSelectorThumbnail = "div.hposter__card > img"
    override val mangaDetailsSelectorGenre = "div.hcol > .hchips--genres > a.chip"
    override val mangaDetailsSelectorTag = "div.hcol > .hchips--tags > a.chip"

    override fun mangaDetailsParse(response: Response): SManga {
        val manga = super.mangaDetailsParse(response)

        manga.description = manga.description?.replace("HAZ CLICK AQUÍ PARA UNIRTE A NUESTRO DISCORD", "", ignoreCase = false)?.trim()

        val removePremium = preferences.getBoolean(REMOVE_PREMIUM_CHAPTERS, REMOVE_PREMIUM_CHAPTERS_DEFAULT)
        if (removePremium && !manga.genre.isNullOrEmpty()) {
            val allCategories = manga.genre!!.split(",").map { it.trim() }

            val filteredCategories = allCategories.filterNot { item ->
                item.contains("Vip", ignoreCase = true) ||
                    item.contains("Premium", ignoreCase = true) ||
                    item.contains("Emperor scan", ignoreCase = true)
            }

            manga.genre = filteredCategories.joinToString(", ")
        }

        return manga
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val scriptData = response.asJsoup().selectFirst("script#mk-chapters-data")!!.data()
        val dto = scriptData.parseAs<ChapterListDto>()

        val removePremium = preferences.getBoolean(REMOVE_PREMIUM_CHAPTERS, REMOVE_PREMIUM_CHAPTERS_DEFAULT)
        val chapters = dto.items

        val filteredChapters = if (removePremium) {
            chapters.filterNot { chapter ->
                chapter.name.contains("Vip", ignoreCase = true) ||
                    chapter.name.contains("Soberano", ignoreCase = true) ||
                    chapter.name.contains("Premium", ignoreCase = true) ||
                    chapter.url.contains("/membership-levels/", ignoreCase = true) ||
                    chapter.st.contains("locked", ignoreCase = true)
            }
        } else {
            chapters
        }

        return filteredChapters.map { chapterDto ->
            SChapter.create().apply {
                setUrlWithoutDomain(chapterDto.url)
                name = chapterDto.name
                date_upload = parseChapterDate(chapterDto.ago)
            }
        }
    }

    private val preferences: SharedPreferences = getPreferences()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        screen.addRandomUAPreference()

        SwitchPreferenceCompat(screen.context).apply {
            key = REMOVE_PREMIUM_CHAPTERS
            title = "Filtrar capítulos VIP"
            summary = "Oculta automáticamente los capítulos VIP"
            setDefaultValue(REMOVE_PREMIUM_CHAPTERS_DEFAULT)
            setOnPreferenceChangeListener { _, _ ->
                Toast.makeText(screen.context, "Para aplicar los cambios, actualiza la lista de capítulos", Toast.LENGTH_LONG).show()
                true
            }
        }.also { screen.addPreference(it) }
    }

    companion object {
        private const val REMOVE_PREMIUM_CHAPTERS = "removePremiumChapters"
        private const val REMOVE_PREMIUM_CHAPTERS_DEFAULT = true
    }
}

package eu.kanade.tachiyomi.extension.ru.rumix

import android.widget.Toast
import androidx.preference.EditTextPreference
import eu.kanade.tachiyomi.multisrc.grouple.GroupLe
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.getPreferences
import org.jsoup.nodes.Document
import java.util.Locale

class RuMIX : GroupLe("RuMIX", "https://rumix.me", "ru") {
    private val preferences = getPreferences()

    override val baseUrl by lazy { getPrefBaseUrl() }

    override val isNeedAuth = true

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.selectFirst(".expandable")!!

        val rawCategory = infoElement.select("span.elem_category").text()
        val category = rawCategory.ifEmpty { "manga" }
        val rawAgeStop = normalizeAgeRating(
            infoElement.selectFirst(".elem_limitation .element-link")?.text().orEmpty(),
        )

        val ratingValue = (infoElement.select(".rating-block").attr("data-score").toFloatOrNull() ?: 0f) * 2
        val ratingValueOver =
            infoElement.select(".info-icon").attr("data-content").substringBeforeLast("/5</b><br/>").substringAfterLast(": <b>")
                .replace(",", ".").toFloatOrNull()?.times(2) ?: 0f
        val ratingVotes = infoElement.select(".col-sm-6 .user-rating meta[itemprop=\"ratingCount\"]").attr("content").ifBlank { "0" }

        val manga = SManga.create()
        manga.title = document.select(".names > .name").text()
        manga.author = infoElement.selectFirst("span.elem_author")?.text() ?: infoElement.selectFirst("span.elem_screenwriter")?.text()
        manga.artist = infoElement.selectFirst("span.elem_illustrator")?.text()

        val rawTags = infoElement.select("a[href*=\"/list/genre/\"], a[href*=\"/list/tag/\"]").map { it.text() }

        manga.genre = listOf(category, rawAgeStop).plus(rawTags).map { it.trim().lowercase(Locale.ROOT) }.filter { it.isNotEmpty() }
            .joinToString(", ")

        val altName =
            infoElement.selectFirst(".another-names")?.text()?.takeIf { it.isNotBlank() }?.let { "Альтернативные названия:\n$it\n\n" }
                .orEmpty()

        val descriptionText = document.select("div#tab-description .manga-description").text()
        val ratingSummary = if (ratingValue > 0f) {
            "${ratingToStars(ratingValue)} $ratingValue[$ratingValueOver] (голосов: $ratingVotes)\n"
        } else {
            ""
        }
        manga.description = ratingSummary + altName + descriptionText

        val pageHtml = document.html().lowercase(Locale.ROOT)
        val badgesText = infoElement.select("span.badge").joinToString(" ") { it.text().lowercase(Locale.ROOT) }
        val hasRestrictedBanner =
            (pageHtml.contains("запрещен") && pageHtml.contains("копирайт")) || (pageHtml.contains("территории рф") && pageHtml.contains("запрещен"))

        manga.status = when {
            hasRestrictedBanner && document.select("div.chapters").isEmpty() -> SManga.LICENSED

            infoElement.html().contains("<b>Сингл") -> SManga.COMPLETED

            badgesText.contains("продолж") || badgesText.contains("начат") -> SManga.ONGOING

            badgesText.contains("заверш") -> if (badgesText.contains("переведен")) {
                SManga.COMPLETED
            } else {
                SManga.PUBLISHING_FINISHED
            }

            badgesText.contains("приост") || badgesText.contains("заморож") -> SManga.ON_HIATUS

            else -> SManga.UNKNOWN
        }

        manga.thumbnail_url = infoElement.selectFirst("img")?.let { img ->
            img.attr("data-full").ifEmpty { img.attr("data-original") }.ifEmpty { img.attr("src") }
        }.orEmpty()

        return manga
    }

    override fun getFilterList() = FilterList(
        OrderBy(),
        CategoryList(getCategoryList()),
        GenreList(getGenreList()),
        AgeList(getAgeList()),
        AdditionalFilterList(getAdditionalFilterList()),
    )

    private fun getAdditionalFilterList() = listOf(
        Genre("Высокий рейтинг", "s_high_rate"),
        Genre("Сингл", "s_single"),
        Genre("Для взрослых", "s_mature"),
        Genre("Завершенная", "s_completed"),
        Genre("Переведено", "s_translated"),
        Genre("Заброшен перевод", "s_abandoned_popular"),
        Genre("Длинная", "s_many_chapters"),
        Genre("Ожидает загрузки", "s_wait_upload"),
        Genre("Онгоинг", "s_ongoing"),
    )

    private fun getAgeList() = listOf(
        Genre("0+", "el_9154"),
        Genre("6+", "el_9155"),
        Genre("12+", "el_9156"),
        Genre("16+", "el_9139"),
        Genre("18+", "el_9145"),
        Genre("G", "el_6180"),
        Genre("PG", "el_6179"),
        Genre("PG-13", "el_6181"),
    )

    private fun getCategoryList() = listOf(
        Genre("BD", "el_9142"),
        Genre("В цвете", "el_7290"),
        Genre("Веб", "el_2160"),
        Genre("Ёнкома", "el_2161"),
        Genre("Комикс", "el_3515"),
        Genre("Манхва", "el_3001"),
        Genre("Маньхуа", "el_3002"),
        Genre("Ранобэ", "el_8575"),
        Genre("Сборник", "el_2157"),
    )

    private fun getGenreList() = listOf(
        Genre("арт", "el_5685"),
        Genre("боевик", "el_2155"),
        Genre("боевые искусства", "el_2143"),
        Genre("вампиры", "el_2148"),
        Genre("вестерн", "el_9150"),
        Genre("гарем", "el_2142"),
        Genre("гендерная интрига", "el_2156"),
        Genre("героическое фэнтези", "el_2146"),
        Genre("детектив", "el_2152"),
        Genre("дзёсэй", "el_2158"),
        Genre("драма", "el_2118"),
        Genre("игра", "el_2154"),
        Genre("история", "el_2119"),
        Genre("киберпанк", "el_8032"),
        Genre("кодомо", "el_2137"),
        Genre("комедия", "el_2136"),
        Genre("махо-сёдзё", "el_2147"),
        Genre("меха", "el_2126"),
        Genre("мистика", "el_9151"),
        Genre("научная фантастика", "el_2133"),
        Genre("повседневность", "el_2135"),
        Genre("постапокалиптика", "el_2151"),
        Genre("приключения", "el_2130"),
        Genre("психология", "el_2144"),
        Genre("романтика", "el_2121"),
        Genre("самурайский боевик", "el_2124"),
        Genre("сверхъестественное", "el_2159"),
        Genre("сёдзё", "el_2122"),
        Genre("сёнэн", "el_2134"),
        Genre("спорт", "el_2129"),
        Genre("сэйнэн", "el_2138"),
        Genre("трагедия", "el_2153"),
        Genre("триллер", "el_2150"),
        Genre("ужасы", "el_2125"),
        Genre("фантастика", "el_9153"),
        Genre("фэнтези", "el_2131"),
        Genre("школа", "el_2127"),
        Genre("этти", "el_2149"),
    )

    override fun setupPreferenceScreen(screen: androidx.preference.PreferenceScreen) {
        super.setupPreferenceScreen(screen)
        EditTextPreference(screen.context).apply {
            key = DOMAIN_PREF
            title = DOMAIN_TITLE
            setDefaultValue(super.baseUrl)
            dialogTitle = DOMAIN_TITLE
            dialogMessage = "Default URL:\n\t${super.baseUrl}"
            setOnPreferenceChangeListener { _, _ ->
                Toast.makeText(
                    screen.context,
                    "Для смены домена необходимо перезапустить приложение с полной остановкой.",
                    Toast.LENGTH_LONG,
                ).show()
                true
            }
        }.let(screen::addPreference)
    }

    private fun getPrefBaseUrl(): String = preferences.getString(DOMAIN_PREF, super.baseUrl)!!

    init {
        preferences.getString(DEFAULT_DOMAIN_PREF, null).let { defaultBaseUrl ->
            if (defaultBaseUrl != super.baseUrl) {
                preferences.edit().putString(DOMAIN_PREF, super.baseUrl).putString(DEFAULT_DOMAIN_PREF, super.baseUrl).apply()
            }
        }
    }

    companion object {
        private const val DOMAIN_PREF = "Домен"
        private const val DEFAULT_DOMAIN_PREF = "pref_default_domain"
        private const val DOMAIN_TITLE = "Домен"
    }
}

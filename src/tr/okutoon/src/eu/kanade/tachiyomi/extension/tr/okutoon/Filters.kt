package eu.kanade.tachiyomi.extension.tr.okutoon

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl

interface UrlFilter {
    fun addToUrl(url: HttpUrl.Builder)
}

open class SelectFilter(
    displayName: String,
    private val param: String,
    private val options: List<Pair<String, String>>,
    defaultValue: String = "",
) : Filter.Select<String>(displayName, options.map { it.first }.toTypedArray(), options.indexOfFirst { it.second == defaultValue }),
    UrlFilter {
    override fun addToUrl(url: HttpUrl.Builder) {
        options[state].second.takeIf { it.isNotEmpty() }?.let { url.addQueryParameter(param, it) }
    }
}

class SortFilter(defaultValue: String = "popular") :
    SelectFilter(
        "Sıralama",
        "sort",
        listOf(
            "En yeni" to "newest",
            "Son güncellenen" to "updated",
            "En popüler" to "popular",
            "En yüksek puan" to "rating",
            "A-Z" to "az",
        ),
        defaultValue,
    )

class PeriodFilter :
    SelectFilter(
        "Popülerlik dönemi",
        "period",
        listOf(
            "Bugün" to "daily",
            "Haftalık" to "weekly",
            "Aylık" to "monthly",
            "Tüm zamanlar" to "all_time",
        ),
        defaultValue = "daily",
    )

class StatusFilter :
    SelectFilter(
        "Durum",
        "status",
        listOf(
            "Her durum" to "",
            "Devam ediyor" to "ongoing",
            "Tamamlandı" to "completed",
            "Ara verildi" to "hiatus",
        ),
    )

class TypeFilter :
    SelectFilter(
        "Tip",
        "type",
        listOf(
            "Tümü" to "",
            "Manga" to "manga",
            "Webtoon" to "webtoon",
        ),
    )

class FlagFilter(name: String, private val param: String) :
    Filter.CheckBox(name),
    UrlFilter {
    override fun addToUrl(url: HttpUrl.Builder) {
        if (state) url.addQueryParameter(param, "1")
    }
}

class ChapterCountFilter(name: String, private val param: String) :
    Filter.Text(name),
    UrlFilter {
    override fun addToUrl(url: HttpUrl.Builder) {
        state.trim().toIntOrNull()?.let { url.addQueryParameter(param, it.toString()) }
    }
}

class Genre(name: String, val slug: String) : Filter.CheckBox(name)

class GenreFilter(genres: List<Genre>) :
    Filter.Group<Genre>("Türler", genres),
    UrlFilter {
    override fun addToUrl(url: HttpUrl.Builder) {
        state.filter { it.state }.forEach { url.addQueryParameter("categories[]", it.slug) }
    }
}

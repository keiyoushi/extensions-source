package eu.kanade.tachiyomi.extension.ar.procomic

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import kotlinx.serialization.Serializable
import java.util.Calendar

abstract class SelectFilter<T>(
    name: String,
    private val options: List<Pair<String, T>>,
) : Filter.Select<String?>(
    name,
    options.map { it.first }.toTypedArray(),
) {
    val selected get() = options[state].second
}

class TypeFilter :
    SelectFilter<String?>(
        name = "النوع",
        options = listOf(
            "الكل" to null,
            "مانجا" to "manga",
            "مانها" to "manhua",
            "مانهوا" to "manhwa",
        ),
    )

class SortFilter :
    SelectFilter<String>(
        name = "الفرز",
        options = listOf(
            "الأكثر شهرة" to "popular",
            "أحدث السلاسل" to "latest",
            "أحدث الفصول" to "latest_chapter",
            "الشعبية الإجمالية" to "total_popularity",
            "الأقدم" to "oldest",
            "أبجدي (أ-ي)" to "az",
            "أبجدي (ي-أ)" to "za",
        ),
    )

class StatusFilter :
    SelectFilter<String?>(
        name = "الحالة",
        options = listOf(
            "جميع الحالات" to null,
            "مستمر" to "مستمر",
            "مكتمل" to "مكتمل",
            "متوقف" to "متوقف",
        ),
    )

private val currentYear = Calendar.getInstance()[Calendar.YEAR]

class YearFilter :
    SelectFilter<String?>(
        name = "السنة",
        options = buildList {
            add("جميع السنوات" to null)
            (currentYear downTo 1970).mapTo(this) { it.toString() to it.toString() }
        },
    )

class GenreFilter(genres: List<Pair<String, String>> = emptyList()) : Filter.Group<GenreFilter.Genre>("التصنيفات", genres.map { Genre(it.first, it.second) }) {
    val checked get() = state.filter { it.state }.map { it.id }

    class Genre(name: String, val id: String) : Filter.CheckBox(name)
}

class TagFilter(tags: List<Pair<String, String>> = emptyList()) : Filter.Group<TagFilter.Tag>("التصنيفات الفرعية", tags.map { Tag(it.first, it.second) }) {
    val checked get() = state.filter { it.state }.map { it.id }

    class Tag(name: String, val id: String) : Filter.CheckBox(name)
}

@Serializable
data class Category(
    val id: Int,
    val en: String,
    val ar: String,
    val descriptionEn: String? = null,
    val descriptionAr: String? = null,
)

object CategoriesCache {
    private val lock = Any()
    private var cachedGenres: List<Pair<String, String>>? = null
    private var cachedTags: List<Pair<String, String>>? = null

    fun getGenres(source: Procomic): List<Pair<String, String>> {
        if (cachedGenres == null) {
            synchronized(lock) {
                if (cachedGenres == null) fetch(source)
            }
        }
        return cachedGenres ?: emptyList()
    }

    fun getTags(source: Procomic): List<Pair<String, String>> {
        if (cachedTags == null) {
            synchronized(lock) {
                if (cachedTags == null) fetch(source)
            }
        }
        return cachedTags ?: emptyList()
    }

    private fun fetch(source: Procomic) {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        try {
            val genresResponse = source.client.newCall(
                GET("${source.baseUrl}/api/public/categories/series/genres", source.headers),
            ).execute().body?.string() ?: "[]"

            val tagsResponse = source.client.newCall(
                GET("${source.baseUrl}/api/public/categories/series/tags", source.headers),
            ).execute().body?.string() ?: "[]"

            cachedGenres = json.decodeFromString<List<Category>>(genresResponse)
                .filter { it.ar.isNotBlank() }
                .map { it.ar to it.en }
                .sortedBy { it.first }

            cachedTags = json.decodeFromString<List<Category>>(tagsResponse)
                .filter { it.ar.isNotBlank() }
                .map { it.ar to it.en }
                .sortedBy { it.first }
        } catch (_: Exception) {
            cachedGenres = emptyList()
            cachedTags = emptyList()
        }
    }
}

package eu.kanade.tachiyomi.extension.fr.bigsolo

import eu.kanade.tachiyomi.source.model.SManga
import org.json.JSONArray
import org.json.JSONObject

/**
 * Data Transfer Objects for BigSolo extension
 */

data class ConfigResponse(
    val env: String?,
    val urlGitCubari: String?,
    val urlRawJsonGithub: String?,
    val urlApiImgchest: String?,
    val localSeriesFiles: List<String>,
)

data class SeriesData(
    val title: String,
    val description: String?,
    val artist: String?,
    val author: String?,
    val coverLow: String?,
    val coverHq: String?,
    val mangaType: String?,
    val magazine: String?,
    val releaseYear: Number?,
    val tags: List<String>?,
    val jpTitle: String?,
    val alternativeTitles: List<String>?,
    val releaseStatus: String?,
    val coversGallery: List<CoversGalleryData>?,
    val episodes: JSONArray?,
    val anime: JSONArray?,
    val chapters: Map<String, ChapterData>?,
)

data class CoversGalleryData(
    val coverLow: String,
    val coverHq: String,
    val volume: String,
)

data class ChapterData(
    val title: String?,
    val volume: String?,
    val lastUpdated: Long?,
    val licencied: Boolean,
    val groups: Map<String, String>?,
)

data class PageData(
    val link: String,
)

// JSON to DTO extension functions
fun JSONObject.toConfigResponse(): ConfigResponse {
    val filesArray = this.getJSONArray("LOCAL_SERIES_FILES")
    val files = List(filesArray.length()) { filesArray.getString(it) }

    return ConfigResponse(
        env = this.optString("ENV"),
        urlGitCubari = this.optString("URL_GIT_CUBARI"),
        urlRawJsonGithub = this.optString("URL_RAW_JSON_GITHUB"),
        urlApiImgchest = this.optString("URL_API_IMGCHEST"),
        localSeriesFiles = files,
    )
}

fun JSONObject.toSeriesData(): SeriesData {
    val tags = this.optJSONArray("tags")?.let { tagsArray ->
        List(tagsArray.length()) { tagsArray.getString(it) }
    }

    val chapters = this.optJSONObject("chapters")?.let { chaptersObj ->
        val chaptersMap = mutableMapOf<String, ChapterData>()
        chaptersObj.keys().forEach { key ->
            val chapterObj = chaptersObj.getJSONObject(key)
            chaptersMap[key] = chapterObj.toChapterData()
        }
        chaptersMap
    }

    return SeriesData(
        title = this.optString("title"),
        description = this.optString("description"),
        artist = this.optString("artist"),
        author = this.optString("author"),
        coverLow = this.optString("cover_low"),
        coverHq = this.optString("cover_hq"),
        mangaType = this.optString("manga_type"),
        magazine = this.optString("magazine"),
        releaseYear = this.optInt("release_year"),
        tags = tags,
        jpTitle = this.optString("jp_title"),
        alternativeTitles = this.optJSONArray("alternative_titles")?.let { altArray ->
            List(altArray.length()) { altArray.getString(it) }
        },
        releaseStatus = this.optString("release_status"),
        coversGallery = this.optJSONArray("covers_gallery")?.let { coversArray ->
            List(coversArray.length()) {
                coversArray.getJSONObject(it).toCoversGalleryData()
            }
        },
        episodes = this.optJSONArray("episodes"),
        anime = this.optJSONArray("anime"),
        chapters = chapters,
    )
}

fun JSONObject.toChapterData(): ChapterData {
    val groups = this.optJSONObject("groups")?.let { groupsObj ->
        val groupsMap = mutableMapOf<String, String>()
        groupsObj.keys().forEach { groupKey ->
            groupsMap[groupKey] = groupsObj.getString(groupKey)
        }
        groupsMap
    }

    return ChapterData(
        title = this.optString("title"),
        volume = this.optString("volume"),
        lastUpdated = this.optLong("last_updated"),
        licencied = this.optBoolean("licencied", false),
        groups = groups,
    )
}

fun JSONObject.toCoversGalleryData(): CoversGalleryData {
    return CoversGalleryData(
        coverLow = this.optString("cover_low"),
        coverHq = this.optString("cover_hq"),
        volume = this.optString("volume"),
    )
}

fun JSONObject.toPageData(): PageData {
    return PageData(
        link = this.optString("link"),
    )
}

// DTO to SManga extension functions
fun SeriesData.toSManga(): SManga = SManga.create().apply {
    title = this@toSManga.title
    artist = this@toSManga.artist
    author = this@toSManga.author
    thumbnail_url = this@toSManga.coverLow
    url = "/${toSlug(this@toSManga.title)}"
}

fun SeriesData.toDetailedSManga(): SManga = SManga.create().apply {
    title = this@toDetailedSManga.title
    description = this@toDetailedSManga.description
    artist = this@toDetailedSManga.artist
    author = this@toDetailedSManga.author
    genre = this@toDetailedSManga.tags?.joinToString(", ") ?: ""
    status = when (this@toDetailedSManga.releaseStatus) {
        "En cours" -> SManga.ONGOING
        "Finis", "Fini" -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }
    thumbnail_url = this@toDetailedSManga.coverHq
    url = "/${toSlug(this@toDetailedSManga.title)}"
}

// Utility function for slug generation
// URLs are manually calculated using a slugify function
fun toSlug(input: String?): String {
    if (input == null) return ""

    val accentsMap = mapOf(
        'à' to 'a', 'á' to 'a', 'â' to 'a', 'ä' to 'a', 'ã' to 'a',
        'è' to 'e', 'é' to 'e', 'ê' to 'e', 'ë' to 'e',
        'ì' to 'i', 'í' to 'i', 'î' to 'i', 'ï' to 'i',
        'ò' to 'o', 'ó' to 'o', 'ô' to 'o', 'ö' to 'o', 'õ' to 'o',
        'ù' to 'u', 'ú' to 'u', 'û' to 'u', 'ü' to 'u',
        'ç' to 'c', 'ñ' to 'n',
    )

    return input
        .lowercase()
        .map { accentsMap[it] ?: it }
        .joinToString("")
        .replace("[^a-z0-9\\s-]".toRegex(), "")
        .replace("\\s+".toRegex(), "-")
        .replace("-+".toRegex(), "-")
        .trim('-')
}

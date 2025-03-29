package eu.kanade.tachiyomi.extension.all.kavita

import eu.kanade.tachiyomi.extension.all.kavita.dto.ChapterDto
import eu.kanade.tachiyomi.extension.all.kavita.dto.ChapterType
import eu.kanade.tachiyomi.extension.all.kavita.dto.PaginationInfo
import eu.kanade.tachiyomi.extension.all.kavita.dto.SeriesDto
import eu.kanade.tachiyomi.extension.all.kavita.dto.VolumeDto
import eu.kanade.tachiyomi.lib.i18n.Intl
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class KavitaHelper {
    val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
        allowSpecialFloatingPointValues = true
        useArrayPolymorphism = true
        prettyPrint = true
    }
    inline fun <reified T : Enum<T>> safeValueOf(type: String): T {
        return java.lang.Enum.valueOf(T::class.java, type)
    }
    val dateFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSSSS", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }
    fun parseDate(dateAsString: String): Long =
        dateFormatter.parse(dateAsString)?.time ?: 0

    fun hasNextPage(response: Response): Boolean {
        val paginationHeader = response.header("Pagination")
        var hasNextPage = false
        if (!paginationHeader.isNullOrEmpty()) {
            val paginationInfo = json.decodeFromString<PaginationInfo>(paginationHeader)
            hasNextPage = paginationInfo.currentPage + 1 > paginationInfo.totalPages
        }
        return !hasNextPage
    }

    fun getIdFromUrl(url: String): Int {
        return url.split("/").last().toInt()
    }

    fun createSeriesDto(obj: SeriesDto, baseUrl: String, apiKey: String): SManga =
        SManga.create().apply {
            url = "$baseUrl/Series/${obj.id}"
            title = obj.name
            // Deprecated: description = obj.summary
            thumbnail_url = "$baseUrl/image/series-cover?seriesId=${obj.id}&apiKey=$apiKey"
        }
    class CompareChapters {
        companion object : Comparator<SChapter> {
            override fun compare(a: SChapter, b: SChapter): Int {
                if (a.chapter_number < 1.0 && b.chapter_number < 1.0) {
                    // Both are volumes, multiply by 100 and do normal sort
                    return if ((a.chapter_number * 100) < (b.chapter_number * 100)) {
                        1
                    } else {
                        -1
                    }
                } else {
                    if (a.chapter_number < 1.0 && b.chapter_number >= 1.0) {
                        // A is volume, b is not. A should sort first
                        return 1
                    } else if (a.chapter_number >= 1.0 && b.chapter_number < 1.0) {
                        return -1
                    }
                }
                if (a.chapter_number < b.chapter_number) return 1
                if (a.chapter_number > b.chapter_number) return -1
                return 0
            }
        }
    }

    fun chapterFromVolume(chapter: ChapterDto, volume: VolumeDto): SChapter =
        SChapter.create().apply {
            val type = ChapterType.of(chapter, volume)

            name = when (type) {
                ChapterType.Regular -> "Volume ${volume.number} Chapter ${chapter.number}"
                ChapterType.SingleFileVolume -> "Volume ${volume.number}"
                ChapterType.Special -> chapter.range
                ChapterType.LooseLeaf -> {
                    val cleanedName = chapter.title.replaceFirst("^0+(?!$)".toRegex(), "")
                    "Chapter $cleanedName"
                }
            }

            chapter_number = if (type == ChapterType.SingleFileVolume) {
                volume.number.toFloat() / 10000
            } else {
                chapter.number.toFloat()
            }

            url = chapter.id.toString()

            if (chapter.fileCount > 1) {
                // salt/offset to recognize chapters with new merged part-chapters as new and hence unread
                chapter_number += 0.001f * chapter.fileCount
                url = "${url}_${chapter.fileCount}"
            }

            date_upload = parseDate(chapter.created)
            scanlator = "${chapter.pages} pages"
        }

    val intl = Intl(
        language = Locale.getDefault().toString(),
        baseLanguage = "en",
        availableLanguages = KavitaInt.AVAILABLE_LANGS,
        classLoader = this::class.java.classLoader!!,
        createMessageFileName = { lang ->
            when (lang) {
                KavitaInt.SPANISH_LATAM -> Intl.createDefaultMessageFileName(KavitaInt.SPANISH)
                else -> Intl.createDefaultMessageFileName(lang)
            }
        },
    )
}

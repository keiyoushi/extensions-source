package eu.kanade.tachiyomi.extension.en.mangafun

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import net.pearx.kasechange.toKebabCase
import java.text.SimpleDateFormat
import java.util.Locale

object MangaFunUtils {
    private const val cdnUrl = "https://mimg.bid"

    private val notAlnumRegex = Regex("""[^0-9A-Za-z\s]""")

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT)

    private fun String.slugify(): String =
        this.replace(notAlnumRegex, "").toKebabCase()

    private fun publishedStatusToStatus(ps: Int) = when (ps) {
        0 -> SManga.ONGOING
        1 -> SManga.COMPLETED
        2 -> SManga.ON_HIATUS
        3 -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    fun convertShortTime(value: Int): Int {
        return if (value < MangaFun.MANGAFUN_EPOCH) {
            value + MangaFun.MANGAFUN_EPOCH
        } else {
            value
        }
    }

    fun getImageUrlFromHash(hash: String?): String? {
        if (hash == null) {
            return null
        }

        return "$cdnUrl/${hash.substring(0, 2)}/${hash.substring(2, 5)}/${hash.substring(5)}.webp"
    }

    fun MinifiedMangaDto.toSManga() = SManga.create().apply {
        url = "/title/$id-${name.slugify()}"
        title = name
        author = this@toSManga.author.joinToString()
        thumbnail_url = getImageUrlFromHash(thumbnailUrl)
        status = publishedStatusToStatus(publishedStatus)
        genre = buildList {
            titleTypeMap[titleType]?.let { add(it) }
            addAll(genres.mapNotNull { genresMap[it] })
        }.joinToString()
    }

    fun MangaDto.toSManga() = SManga.create().apply {
        url = "/title/$id-${name.slugify()}"
        title = name
        author = this@toSManga.author.filterNotNull().joinToString()
        artist = this@toSManga.artist.filterNotNull().joinToString()
        description = this@toSManga.description
        genre = genres.mapNotNull { genresMap[it.id] }.joinToString()
        status = publishedStatusToStatus(publishedStatus)
        thumbnail_url = thumbnailURL
        genre = buildList {
            titleTypeMap[titleType]?.let { add(it) }
            addAll(genres.mapNotNull { genresMap[it.id] })
        }.joinToString()
    }

    fun ChapterDto.toSChapter(mangaId: Int, mangaName: String) = SChapter.create().apply {
        url = "/title/$mangaId-${mangaName.slugify()}/$id-${this@toSChapter.name.slugify()}"
        name = this@toSChapter.name
        date_upload = runCatching {
            dateFormat.parse(publishedAt)!!.time
        }.getOrDefault(0L)
    }
}

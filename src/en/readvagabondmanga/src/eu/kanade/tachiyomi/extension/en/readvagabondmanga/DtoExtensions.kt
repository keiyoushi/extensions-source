package eu.kanade.tachiyomi.extension.en.readvagabondmanga

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import java.text.SimpleDateFormat
import java.util.Locale

fun MangaDto.toSManga(): SManga = SManga.create().apply {
    title = this@toSManga.title
    url = "/#${this@toSManga.id}"
    thumbnail_url = this@toSManga.cover
    author = this@toSManga.author
    artist = this@toSManga.artist
    description = this@toSManga.description
    status = when (this@toSManga.status) {
        MangaStatus.ONGOING -> SManga.ONGOING
        MangaStatus.COMPLETED -> SManga.COMPLETED
        MangaStatus.HIATUS -> SManga.ON_HIATUS
    }
}

fun ChapterDto.toSChapter(): SChapter = SChapter.create().apply {
    name = this@toSChapter.title
    chapter_number = this@toSChapter.number.toFloat()
    url = "/volume-$volume/chapter-$number/#${this@toSChapter.mangaId}"
    date_upload = dateFormat.tryParse(this@toSChapter.releaseDate)
    scanlator = "Read Vagabond Manga"
}

private val dateFormat by lazy {
    SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
}

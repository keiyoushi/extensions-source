package eu.kanade.tachiyomi.extension.en.zeroscans

import eu.kanade.tachiyomi.source.model.SManga
import java.util.Calendar
import java.util.Locale

class ZeroScansHelper {

    // Search Related
    fun checkStatusFilter(
        filter: ZeroScans.StatusFilter,
        comic: ZeroScansComicDto,
    ): Boolean {
        val includedStatusIds = filter.state.filter { it.isIncluded() }.map { it.id }
        val excludedStatusIds = filter.state.filter { it.isExcluded() }.map { it.id }

        val comicStatusesId = comic.statuses.map { it.id }

        if (includedStatusIds.isEmpty() && excludedStatusIds.isEmpty()) return true

        return includedStatusIds.any { it in comicStatusesId } && excludedStatusIds.any { it !in comicStatusesId }
    }

    fun checkGenreFilter(
        filter: ZeroScans.GenreFilter,
        comic: ZeroScansComicDto,
    ): Boolean {
        val includedGenreIds = filter.state.filter { it.isIncluded() }.map { it.id }
        val excludedGenreIds = filter.state.filter { it.isExcluded() }.map { it.id }

        val comicStatusesId = comic.genres.map { it.id }

        if (includedGenreIds.isEmpty() && excludedGenreIds.isEmpty()) return true

        return includedGenreIds.any { it in comicStatusesId } && excludedGenreIds.any { it !in comicStatusesId }
    }

    fun applySortFilter(
        type: String,
        ascending: Boolean,
        comics: List<ZeroScansComicDto>,
    ): List<ZeroScansComicDto> {
        var sortedList = when (type) {
            "alphabetic" -> comics.sortedBy { it.name.lowercase(Locale.ROOT) }
            "rating" -> comics.sortedBy { it.getRating() }
            "chapter_count" -> comics.sortedBy { it.chapterCount }
            "bookmark_count" -> comics.sortedBy { it.bookmarkCount }
            "view_count" -> comics.sortedBy { it.viewCount }
            else -> comics
        }

        if (!ascending) {
            sortedList = sortedList.reversed()
        }

        return sortedList
    }

    // Chapter Related
    fun parseChapterUploadDate(date: String): Long {
        val value = date.split(' ')[0].toInt()

        return when (date.split(' ')[1].removeSuffix("s")) {
            "sec" -> Calendar.getInstance().apply {
                add(Calendar.SECOND, value * -1)
            }.timeInMillis
            "min" -> Calendar.getInstance().apply {
                add(Calendar.MINUTE, value * -1)
            }.timeInMillis
            "hour" -> Calendar.getInstance().apply {
                add(Calendar.HOUR_OF_DAY, value * -1)
            }.timeInMillis
            "day" -> Calendar.getInstance().apply {
                add(Calendar.DATE, value * -1)
            }.timeInMillis
            "week" -> Calendar.getInstance().apply {
                add(Calendar.DATE, value * 7 * -1)
            }.timeInMillis
            "month" -> Calendar.getInstance().apply {
                add(Calendar.MONTH, value * -1)
            }.timeInMillis
            "year" -> Calendar.getInstance().apply {
                add(Calendar.YEAR, value * -1)
            }.timeInMillis
            else -> {
                return 0
            }
        }
    }

    // Manga Related
    fun zsComicEntryToSManga(comic: ZeroScansComicDto): SManga {
        var comicDescription = comic.summary
        if (comic.statuses.any { it.id == 4 }) {
            comicDescription = "The series has been dropped.\n\n$comicDescription"
        }
        return SManga.create().apply {
            title = comic.name
            url = "/comics/${comic.slug}?id=${comic.id}"
            thumbnail_url = comic.cover.getHighResCover()
            description = comicDescription
            genre = comic.genres.joinToString { it.name }
            status = comic.getTachiyomiStatus()
            initialized = true
        }
    }

    private fun ZeroScansComicDto.getTachiyomiStatus(): Int {
        // 1 = New & 4 = Dropped
        val compatibleStatus = statuses.filterNot { it.id in listOf(1, 4) }

        // TODO Apply 6 to ON_HIATUS after ext-lib 1.3 merge
        compatibleStatus.firstOrNull { it.id in listOf(5, 6) }
            ?.also { return SManga.ONGOING }

        compatibleStatus.firstOrNull { it.id == 3 }
            ?.also { return SManga.COMPLETED }

        // Nothing Matched
        return SManga.UNKNOWN
    }
}

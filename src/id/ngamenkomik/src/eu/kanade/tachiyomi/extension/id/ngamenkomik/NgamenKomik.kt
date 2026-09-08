package eu.kanade.tachiyomi.extension.id.ngamenkomik

import eu.kanade.tachiyomi.multisrc.zeistmanga.Genre
import eu.kanade.tachiyomi.multisrc.zeistmanga.Status
import eu.kanade.tachiyomi.multisrc.zeistmanga.Type
import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient

@Source
abstract class NgamenKomik : ZeistManga() {
    override fun OkHttpClient.Builder.configureClient() = rateLimit(3)

    // ============================== Filters ===============================
    override val hasFilters = true

    override val hasLanguageFilter = false

    override fun getTypeList() = listOf(
        Type("Semua", ""),
        Type("Manhua", "Manhua"),
        Type("Manhwa", "Manhwa"),
    )

    override fun getStatusList() = listOf(
        Status("Semua", ""),
        Status("Ongoing", "Ongoing"),
        Status("Completed", "Completed"),
    )

    override fun getGenreList() = listOf(
        "Action", "Adventure", "Comedy", "Drama",
        "Ecchi", "Fantasy", "Harem", "Horror",
        "Isekai", "Magic", "Martial Arts", "Mystery",
        "Reincarnation", "Romance", "School Life", "Shounen",
        "Slice of Life", "Supernatural", "Thriller",
    ).map { Genre(it, it) }
}

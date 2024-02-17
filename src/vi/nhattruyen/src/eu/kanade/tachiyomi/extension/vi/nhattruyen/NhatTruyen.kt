package eu.kanade.tachiyomi.extension.vi.nhattruyen

import eu.kanade.tachiyomi.multisrc.wpcomics.WPComics
import eu.kanade.tachiyomi.source.model.MangasPage
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale

class NhatTruyen : WPComics("NhatTruyen", "https://nhattruyento.com", "vi", SimpleDateFormat("dd/MM/yy", Locale.getDefault()), null) {
    override val searchPath = "the-loai"

    /**
     * NetTruyen/NhatTruyen redirect back to catalog page if searching query is not found.
     * That makes both sites always return un-relevant results when searching should return empty.
     */
    override fun searchMangaParse(response: Response): MangasPage {
        if (response.request.url.queryParameter(name = queryParam) == null) {
            return MangasPage(mangas = listOf(), hasNextPage = false)
        }
        return super.searchMangaParse(response)
    }

    override fun getGenreList(): Array<Pair<String?, String>> = arrayOf(
        null to "Tất cả",
        "action" to "Action",
        "adult" to "Adult",
        "adventure" to "Adventure",
        "anime" to "Anime",
        "chuyen-sinh" to "Chuyển Sinh",
        "comedy" to "Comedy",
        "comic" to "Comic",
        "cooking" to "Cooking",
        "co-dai" to "Cổ Đại",
        "doujinshi" to "Doujinshi",
        "drama" to "Drama",
        "dam-my" to "Đam Mỹ",
        "ecchi" to "Ecchi",
        "fantasy" to "Fantasy",
        "gender-bender" to "Gender Bender",
        "harem" to "Harem",
        "historical" to "Historical",
        "horror" to "Horror",
        "josei" to "Josei",
        "live-action" to "Live action",
        "manga-241" to "Manga",
        "manhua" to "Manhua",
        "manhwa-2431" to "Manhwa",
        "martial-arts" to "Martial Arts",
        "mature" to "Mature",
        "mecha" to "Mecha",
        "mystery" to "Mystery",
        "ngon-tinh" to "Ngôn Tình",
        "one-shot" to "One shot",
        "psychological" to "Psychological",
        "romance" to "Romance",
        "school-life" to "School Life",
        "sci-fi" to "Sci-fi",
        "seinen" to "Seinen",
        "shoujo" to "Shoujo",
        "shoujo-ai" to "Shoujo Ai",
        "shounen" to "Shounen",
        "shounen-ai" to "Shounen Ai",
        "slice-of-life" to "Slice of Life",
        "smut" to "Smut",
        "soft-yaoi" to "Soft Yaoi",
        "soft-yuri" to "Soft Yuri",
        "sports" to "Sports",
        "supernatural" to "Supernatural",
        "tap-chi-truyen-tranh" to "Tạp chí truyện tranh",
        "thieu-nhi" to "Thiếu Nhi",
        "tragedy" to "Tragedy",
        "trinh-tham" to "Trinh Thám",
        "truyen-scan" to "Truyện scan",
        "truyen-mau" to "Truyện Màu",
        "viet-nam" to "Việt Nam",
        "webtoon" to "Webtoon",
        "xuyen-khong" to "Xuyên Không",
        "16" to "16+",
    )
}

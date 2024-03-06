package eu.kanade.tachiyomi.extension.vi.nettruyen

import eu.kanade.tachiyomi.multisrc.wpcomics.WPComics
import eu.kanade.tachiyomi.source.model.MangasPage
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale

<<<<<<< HEAD
<<<<<<< HEAD
class NetTruyen : WPComics("NetTruyen", "https://www.nettruyenkk.com", "vi", SimpleDateFormat("dd/MM/yy", Locale.getDefault()), null) {
=======
class NetTruyen : WPComics("NetTruyen", "https://www.nettruyenbb.com", "vi", SimpleDateFormat("dd/MM/yy", Locale.getDefault()), null) {
>>>>>>> parent of 5642a0e4 (change domain for nettruyen)
=======
class NetTruyen : WPComics("NetTruyen", "https://www.nettruyenee.com", "vi", SimpleDateFormat("dd/MM/yy", Locale.getDefault()), null) {
>>>>>>> parent of ad9a7b02 (rechanging to original domain)
    override fun String.replaceSearchPath() = replace("/$searchPath?status=2&", "/truyen-full?")

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
        "action-95" to "Action",
        "truong-thanh" to "Adult",
        "adventure" to "Adventure",
        "anime" to "Anime",
        "chuyen-sinh-2131" to "Chuyển Sinh",
        "comedy-99" to "Comedy",
        "comic" to "Comic",
        "cooking-101" to "Cooking",
        "co-dai-207" to "Cổ Đại",
        "doujinshi" to "Doujinshi",
        "drama-103" to "Drama",
        "dam-my" to "Đam Mỹ",
        "ecchi-104" to "Ecchi",
        "fantasy-1050" to "Fantasy",
        "gender-bender-106" to "Gender Bender",
        "harem-107" to "Harem",
        "historical" to "Historical",
        "horror" to "Horror",
        "josei" to "Josei",
        "live-action" to "Live action",
        "manga-112" to "Manga",
        "manhua" to "Manhua",
        "manhwa-11400" to "Manhwa",
        "martial-arts" to "Martial Arts",
        "mature" to "Mature",
        "mecha-117" to "Mecha",
        "mystery" to "Mystery",
        "ngon-tinh" to "Ngôn Tình",
        "one-shot" to "One shot",
        "psychological" to "Psychological",
        "romance-121" to "Romance",
        "school-life" to "School Life",
        "sci-fi" to "Sci-fi",
        "seinen" to "Seinen",
        "shoujo" to "Shoujo",
        "shoujo-ai-126" to "Shoujo Ai",
        "shounen-127" to "Shounen",
        "shounen-ai" to "Shounen Ai",
        "slice-of-life" to "Slice of Life",
        "smut" to "Smut",
        "soft-yaoi" to "Soft Yaoi",
        "soft-yuri" to "Soft Yuri",
        "sports" to "Sports",
        "supernatural" to "Supernatural",
        "tap-chi-truyen-tranh" to "Tạp chí truyện tranh",
        "thieu-nhi" to "Thiếu Nhi",
        "tragedy-136" to "Tragedy",
        "trinh-tham" to "Trinh Thám",
        "truyen-scan" to "Truyện scan",
        "truyen-mau-214" to "Truyện Màu",
        "viet-nam" to "Việt Nam",
        "webtoon-140" to "Webtoon",
        "xuyen-khong-205" to "Xuyên Không",
        "16" to "16+",
    )
}

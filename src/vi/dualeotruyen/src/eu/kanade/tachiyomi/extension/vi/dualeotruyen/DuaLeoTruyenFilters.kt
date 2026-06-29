package eu.kanade.tachiyomi.extension.vi.dualeotruyen

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters(): FilterList = FilterList(
    GenreFilter(),
)

class GenreFilter : UriPartFilter("Thể loại", getGenreList())

open class UriPartFilter(
    displayName: String,
    private val genres: Array<Genre>,
) : Filter.Select<String>(displayName, genres.map { it.name }.toTypedArray()) {
    fun toUriPart(): String? = genres[state].uriPart
}

class Genre(val name: String, val uriPart: String?)

private fun getGenreList(): Array<Genre> = arrayOf(
    Genre("Tất cả", null),
    Genre("ABO", "/the-loai/abo"),
    Genre("Bách Hợp", "/the-loai/bach-hop"),
    Genre("BoyLove", "/the-loai/boylove"),
    Genre("Chuyển Sinh", "/the-loai/chuyen-sinh"),
    Genre("Cổ Đại", "/the-loai/co-dai"),
    Genre("Doujinshi", "/the-loai/doujinshi"),
    Genre("Drama", "/the-loai/drama"),
    Genre("Đam Mỹ", "/the-loai/dam-my"),
    Genre("Echi", "/the-loai/echi"),
    Genre("GirlLove", "/the-loai/girllove"),
    Genre("Hài Hước", "/the-loai/hai-huoc"),
    Genre("Hành Động", "/the-loai/hanh-dong"),
    Genre("Harem", "/the-loai/harem"),
    Genre("Hentai", "/the-loai/hentai"),
    Genre("Kịch Tính", "/the-loai/kich-tinh"),
    Genre("Lãng Mạn", "/the-loai/lang-man"),
    Genre("Manga", "/the-loai/manga"),
    Genre("Manhua", "/the-loai/manhua"),
    Genre("Manhwa", "/the-loai/manhwa"),
    Genre("Người Thú", "/the-loai/nguoi-thu"),
    Genre("Oneshot", "/the-loai/oneshot"),
    Genre("Phiêu lưu", "/the-loai/phieu-luu"),
    Genre("Tình Cảm", "/the-loai/tinh-cam"),
    Genre("Truyện Màu", "/the-loai/truyen-mau"),
    Genre("18+", "/the-loai/18-"),
    Genre("Yaoi", "/the-loai/yaoi"),
    Genre("Yuri", "/the-loai/yuri"),
)

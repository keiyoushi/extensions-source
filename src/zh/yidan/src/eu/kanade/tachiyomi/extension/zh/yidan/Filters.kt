package eu.kanade.tachiyomi.extension.zh.yidan

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import okhttp3.HttpUrl

fun getFilterListInternal() = FilterList(ListingFilter(), GenreFilter())

fun parseFilters(filters: FilterList, builder: HttpUrl.Builder) {
    for (filter in filters) when (filter) {
        is ListingFilter -> {
            if (filter.state > 0) {
                builder.addEncodedQueryParameter("mhcate", LISTING_VALUES[filter.state].toString())
            }
        }
        is GenreFilter -> {
            if (filter.state > 0) {
                builder.addEncodedQueryParameter("cateid", String.format("%02d", filter.state))
            }
        }
        else -> {}
    }
}

class ListingFilter : Filter.Select<String>("分类", LISTINGS)

val LISTINGS = arrayOf("全部", "排行榜", "新作", "完结漫", "分类0", "分类1", "分类3", "分类7")
val LISTING_VALUES = arrayOf(0, 2, 4, 5, 0, 1, 3, 7)

class GenreFilter : Filter.Select<String>("标签", GENRES)

val GENRES = arrayOf(
    "全部",
    "短漫", // 01
    "甜漫", // 02
    "强强", // 03
    "年下攻", // 04
    "诱受", // 05
    "骨科", // 06
    "调教", // 07
    "健气受", // 08
    "ABO", // 09
    "重生/重逢", // 10
    "财阀", // 11
    "校园", // 12
    "女王受", // 13
    "NP/SM", // 14
    "韩国榜单", // 15
    "高H", // 16
    "架空", // 17
    "娱乐圈", // 18
    "办公室", // 19
    "青梅竹马", // 20
)

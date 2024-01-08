package eu.kanade.tachiyomi.extension.zh.sixmh

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.SChapter
import kotlinx.serialization.Serializable

@Serializable
class ChapterDto(private val chapterid: String, private val chaptername: String) {
    fun toSChapter(path: String) = SChapter.create().apply {
        url = "$path$chapterid.html"
        name = chaptername
    }
}

internal class PageFilter : Filter.Select<String>("排行榜/分类", PAGE_NAMES) {
    val path get() = PAGE_PATHS[state]
}

private val PAGE_NAMES = arrayOf(
    "人气榜", "周读榜", "月读榜", "火爆榜", "更新榜", "新漫榜",
    "冒险热血", "武侠格斗", "科幻魔幻", "侦探推理", "耽美爱情", "生活漫画",
    "推荐漫画", "完结漫画", "连载漫画",
)

private val PAGE_PATHS = arrayOf(
    "/rank/1-", "/rank/2-", "/rank/3-", "/rank/4-", "/rank/5-", "/rank/6-",
    "/sort/1-", "/sort/2-", "/sort/3-", "/sort/4-", "/sort/5-", "/sort/6-",
    "/sort/11-", "/sort/12-", "/sort/13-",
)

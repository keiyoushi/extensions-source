package eu.kanade.tachiyomi.extension.zh.hikarinagi

import eu.kanade.tachiyomi.source.model.Filter

/** A filter that contributes a query parameter to the light novel browse request. */
interface UrlPartFilter {
    fun toUrlPart(): Pair<String, String>?
}

class SortFilter(select: Selection? = null) : Filter.Sort("排序", arrayOf("更新时间", "热度", "收录时间", "发布时间", "标题"), select) {
    private val sort = arrayOf("latest_chapter_at", "heat", "created_at", "publication_date", "title")
    override fun toString() = state?.let { "${sort[state!!.index]}:${if (state!!.ascending) "asc" else "dssc"}" } ?: "latest_chapter_at:desc"
}

class RegionFilter : Filter.Select<String>("地区", arrayOf("全部", "日漫", "韩漫", "国漫", "其他")) {
    override fun toString() = arrayOf("", "jp", "kr", "cn", "other")[state]
}

class AudienceFilter : Filter.Select<String>("受众", arrayOf("全部", "少年", "青年", "少女", "女性")) {
    override fun toString() = arrayOf("", "shonen", "seinen", "shojo", "josei")[state]
}

class StatusFilter :
    Filter.Select<String>("状态", arrayOf("全部", "连载中", "已完结", "休刊")),
    UrlPartFilter {
    override fun toString() = STATUSES[state]

    override fun toUrlPart() = STATUSES[state].takeIf(String::isNotEmpty)?.let { "status" to it }

    companion object {
        private val STATUSES = arrayOf("", "serializing", "finished", "paused")
    }
}

class DecadeFilter :
    Filter.Select<String>("年代", arrayOf("全部", "2020 年代", "2010 年代", "2000 年代", "更早")),
    UrlPartFilter {
    override fun toString() = DECADES[state]

    override fun toUrlPart() = DECADES[state].takeIf(String::isNotEmpty)?.let { "decade" to it }

    companion object {
        private val DECADES = arrayOf("", "2020s", "2010s", "2000s", "earlier")
    }
}

class MagazineFilter :
    Filter.Select<String>(
        "杂志",
        arrayOf(
            "全部", "週刊少年ジャンプ", "少年ジャンプ＋", "週刊少年サンデー", "週刊少年マガジン", "カドコミ", "アルファポリス電網浮遊都市",
            "コミックDAYS", "週刊少年チャンピオン", "モーニング", "ガンガンONLINE", "週刊ヤングマガジン", "週刊ヤングジャンプ",
        ),
    ) {
    override fun toString() = arrayOf(
        "", "10659", "10752", "10678", "10702", "10855", "11315", "10860", "10724", "10673", "10786", "10754", "10687",
    )[state]
}

class NovelSortFilter(select: Selection? = Selection(0, false)) :
    Filter.Sort(
        "排序",
        arrayOf("最近更新", "最多阅读", "最新收录", "发售日 新→旧", "发售日 旧→新"),
        select,
    ),
    UrlPartFilter {
    override fun toUrlPart() = state?.let { "sort" to SORTS[it.index] }

    companion object {
        private val SORTS = arrayOf(
            "revised_at:desc",
            "read_times:desc",
            "created_at:desc",
            "publication_date:desc",
            "publication_date:asc",
        )
    }
}

class NovelReadableFilter :
    Filter.CheckBox("仅显示可在线阅读"),
    UrlPartFilter {
    override fun toUrlPart() = if (state) "readable" to "1" else null
}

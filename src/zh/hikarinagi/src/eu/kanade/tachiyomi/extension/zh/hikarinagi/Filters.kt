package eu.kanade.tachiyomi.extension.zh.hikarinagi

import eu.kanade.tachiyomi.source.model.Filter

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

class StatusFilter : Filter.Select<String>("状态", arrayOf("全部", "连载中", "已完结", "休刊")) {
    override fun toString() = arrayOf("", "serializing", "finished", "paused")[state]
}

class DecadeFilter : Filter.Select<String>("年代", arrayOf("全部", "2020 年代", "2010 年代", "2000 年代", "更早")) {
    override fun toString() = arrayOf("", "2020s", "2010s", "2000s", "earlier")[state]
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

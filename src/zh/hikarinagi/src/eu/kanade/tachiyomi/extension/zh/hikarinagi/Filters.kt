package eu.kanade.tachiyomi.extension.zh.hikarinagi

import eu.kanade.tachiyomi.source.model.Filter

/** A filter that contributes one query parameter to a browse request. */
interface QueryParamFilter {
    fun toQueryParam(): Pair<String, String>?
}

class SortFilter(select: Selection? = null) :
    Filter.Sort("排序", arrayOf("更新时间", "热度", "收录时间", "发布时间", "标题"), select),
    QueryParamFilter {
    private val sort = arrayOf("latest_chapter_at", "heat", "created_at", "publication_date", "title")

    override fun toQueryParam() = "sort" to (state?.let { "${sort[it.index]}:${if (it.ascending) "asc" else "dssc"}" } ?: "latest_chapter_at:desc")
}

class RegionFilter :
    Filter.Select<String>("地区", arrayOf("全部", "日漫", "韩漫", "国漫", "其他")),
    QueryParamFilter {
    override fun toQueryParam() = arrayOf("", "jp", "kr", "cn", "other")[state].takeIf(String::isNotEmpty)?.let { "region" to it }
}

class AudienceFilter :
    Filter.Select<String>("受众", arrayOf("全部", "少年", "青年", "少女", "女性")),
    QueryParamFilter {
    override fun toQueryParam() = arrayOf("", "shonen", "seinen", "shojo", "josei")[state].takeIf(String::isNotEmpty)?.let { "audience" to it }
}

class StatusFilter :
    Filter.Select<String>("状态", arrayOf("全部", "连载中", "已完结", "休刊")),
    QueryParamFilter {
    override fun toQueryParam() = STATUSES[state].takeIf(String::isNotEmpty)?.let { "status" to it }

    companion object {
        private val STATUSES = arrayOf("", "serializing", "finished", "paused")
    }
}

class DecadeFilter :
    Filter.Select<String>("年代", arrayOf("全部", "2020 年代", "2010 年代", "2000 年代", "更早")),
    QueryParamFilter {
    override fun toQueryParam() = DECADES[state].takeIf(String::isNotEmpty)?.let { "decade" to it }

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
    ),
    QueryParamFilter {
    override fun toQueryParam() = arrayOf(
        "", "10659", "10752", "10678", "10702", "10855", "11315", "10860", "10724", "10673", "10786", "10754", "10687",
    )[state].takeIf(String::isNotEmpty)?.let { "magazine_id" to it }
}

class NovelSortFilter(select: Selection? = Selection(0, false)) :
    Filter.Sort(
        "排序",
        arrayOf("最近更新", "最多阅读", "最新收录", "发售日 新→旧", "发售日 旧→新"),
        select,
    ),
    QueryParamFilter {
    override fun toQueryParam() = state?.let { "sort" to SORTS[it.index] }

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

/** Light novel imprints (文庫); the manga browse has magazines instead and ignores this. */
class BunkoFilter :
    Filter.Select<String>("文库", LABELS),
    QueryParamFilter {
    override fun toQueryParam() = IDS.getOrNull(state - 1)?.let { "bunko_id" to it }

    companion object {
        private val BUNKOS = listOf(
            "コバルト文庫" to 2,
            "スーパーダッシュ文庫" to 6,
            "富士見ファンタジア文庫" to 8,
            "角川スニーカー文庫" to 12,
            "電撃文庫" to 14,
            "ガガガ文庫" to 19,
            "メディアワークス文庫" to 25,
            "ファミ通文庫" to 26,
            "カドカワBOOKS" to 31,
            "電撃の新文芸" to 41,
            "ヒーロー文庫" to 42,
            "MF文庫J" to 43,
            "ダッシュエックス文庫" to 47,
            "講談社ラノベ文庫" to 51,
            "GA文庫" to 54,
            "HJ文庫" to 57,
            "角川文庫" to 59,
            "角川ビーンズ文庫" to 61,
            "オーバーラップ文庫" to 63,
            "GAノベル" to 65,
            "富士見ミステリー文庫" to 68,
            "オーバーラップノベルス" to 70,
            "アース・スターノベル" to 71,
            "SQEXノベル" to 73,
            "GCノベルズ" to 79,
            "GCN文庫" to 81,
            "ブレイブ文庫" to 83,
            "富士見L文庫" to 84,
            "HJノベルス" to 87,
            "一迅社文庫アイリス" to 88,
            "MFブックス" to 89,
            "角川ルビー文庫" to 101,
            "KAエスマ文庫" to 104,
            "ビーズログ文庫" to 116,
            "集英社みらい文庫" to 117,
            "角川ホラー文庫" to 121,
            "モンスター文庫" to 130,
            "集英社オレンジ文庫" to 144,
            "Mノベルス" to 150,
            "角川ティーンズルビー文庫" to 160,
            "ことのは文庫" to 170,
            "オーバーラップノベルスf" to 179,
            "Celicaノベルス" to 188,
            "一迅社ノベルス" to 192,
            "アース・スター ルナ" to 193,
            "ダンガン文庫" to 220,
            "小学館文庫" to 225,
            "ドラゴンノベルス" to 230,
            "星海社FICTIONS" to 231,
            "ハガネ文庫" to 240,
            "マッグガーデン・ノベルズ" to 241,
            "月光之城" to 242,
            "サーガフォレスト" to 244,
            "株式会社アスキー・メディアワークス" to 934,
            "Kラノベブックス" to 10647,
            "ツギクルブックス" to 10651,
            "BKブックス" to 10653,
            "レジーナブックス" to 19323,
            "モーニングスターブックス" to 19328,
        )

        private val IDS = BUNKOS.map { it.second.toString() }

        private val LABELS = (listOf("全部") + BUNKOS.map { it.first }).toTypedArray()
    }
}

package eu.kanade.tachiyomi.extension.zh.mh160

import eu.kanade.tachiyomi.source.model.Filter

open class UrlSelectFilter(name: String, private val options: List<Pair<String, String?>>) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    val selected: String? get() = options[state].second
}

class RegionFilter :
    UrlSelectFilter(
        "地区",
        listOf(
            "不限" to null,
            "日韩" to "zaixian_rhmh",
            "内地" to "zaixian_dlmh",
            "港台" to "zaixian_gtmh",
        ),
    )

class GenreFilter :
    UrlSelectFilter(
        "题材",
        listOf(
            "全部" to null,
            "热血" to "rexue",
            "格斗" to "gedou",
            "科幻" to "kehuan",
            "竞技" to "jingji",
            "搞笑" to "gaoxiao",
            "推理" to "tuili",
            "恐怖" to "kongbu",
            "耽美" to "danmei",
            "少女" to "shaonv",
            "恋爱" to "lianai",
            "生活" to "shenghuo",
            "战争" to "zhanzheng",
            "故事" to "gushi",
            "冒险" to "maoxian",
            "魔幻" to "mohuan",
            "玄幻" to "xuanhuan",
            "校园" to "xiaoyuan",
            "悬疑" to "xuanyi",
            "萌系" to "mengxi",
            "穿越" to "chuanyue",
            "后宫" to "hougong",
            "都市" to "dushi",
            "武侠" to "wuxia",
            "历史" to "lishi",
            "同人" to "tongren",
        ),
    )

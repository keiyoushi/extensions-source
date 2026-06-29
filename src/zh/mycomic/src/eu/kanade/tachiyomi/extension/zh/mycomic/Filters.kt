package eu.kanade.tachiyomi.extension.zh.mycomic

import eu.kanade.tachiyomi.source.model.Filter

open class UriPartFilter(
    val key: String,
    name: String,
    private val pairs: List<Pair<String, String>>,
    state: Int = 0,
) : Filter.Select<String>(name, pairs.map { it.first }.toTypedArray(), state) {
    val selected
        get() = pairs[state].second
}

class SortFilter(state: Int) :
    UriPartFilter(
        "sort",
        "排序",
        listOf(
            "最新上架" to "",
            "最近更新" to "-update",
            "最高人气" to "-views",
            "日排行" to RANK_PREFIX,
            "週排行" to "$RANK_PREFIX-week",
            "月排行" to "$RANK_PREFIX-month",
            "歷史排行" to "$RANK_PREFIX-views",
        ),
        state,
    ) {
    companion object {
        const val RANK_PREFIX = "rank|"
    }
}

class RegionFilter :
    UriPartFilter(
        "filter[country]",
        "作品地区",
        listOf(
            "所有" to "",
            "日本" to "japan",
            "港台" to "hongkong",
            "歐美" to "europe",
            "內地" to "china",
            "韓國" to "korea",
            "其他" to "other",
        ),
    )

class TagFilter :
    UriPartFilter(
        "filter[tag]",
        "作品类型",
        listOf(
            "所有" to "",
            "魔幻" to "mohuan",
            "魔法" to "mofa",
            "熱血" to "rexue",
            "冒險" to "maoxian",
            "懸疑" to "xuanyi",
            "偵探" to "zhentan",
            "愛情" to "aiqing",
            "校園" to "xiaoyuan",
            "搞笑" to "gaoxiao",
            "四格" to "sige",
            "科幻" to "kehuan",
            "神鬼" to "shengui",
            "舞蹈" to "wudao",
            "音樂" to "yinyue",
            "百合" to "baihe",
            "後宮" to "hougong",
            "機戰" to "jizhan",
            "格鬥" to "gedou",
            "恐怖" to "kongbu",
            "萌系" to "mengxi",
            "武俠" to "wuxia",
            "社會" to "shehui",
            "歷史" to "lishi",
            "耽美" to "danmei",
            "勵志" to "lizhi",
            "職場" to "zhichang",
            "生活" to "shenghuo",
            "治癒" to "zhiyu",
            "偽娘" to "weiniang",
            "黑道" to "heidao",
            "戰爭" to "zhanzheng",
            "競技" to "jingji",
            "體育" to "tiyu",
            "美食" to "meishi",
            "腐女" to "funv",
            "宅男" to "zhainan",
            "推理" to "tuili",
            "雜誌" to "zazhi",
        ),
    )

class AudienceFilter :
    UriPartFilter(
        "filter[audience]",
        "适合受众",
        listOf(
            "所有" to "",
            "少女" to "shaonv",
            "少年" to "shaonian",
            "青年" to "qingnian",
            "兒童" to "ertong",
            "通用" to "tongyong",
        ),
    )

class YearFilter :
    UriPartFilter(
        "filter[year]",
        "出品年份",
        listOf(
            "所有" to "",
            "2025" to "2025",
            "2024" to "2024",
            "2023" to "2023",
            "2022" to "2022",
            "2021" to "2021",
            "2020" to "2020",
            "2019" to "2019",
            "2018" to "2018",
            "2017" to "2017",
            "2016" to "2016",
            "2015" to "2015",
            "2014" to "2014",
            "2013" to "2013",
            "2012" to "2012",
            "2011" to "2011",
            "2010" to "2010",
            "00年代" to "200x",
            "90年代" to "199x",
            "80年代" to "198x",
            "70年代或更早" to "197x",
        ),
    )

class StatusFilter :
    UriPartFilter(
        "filter[end]",
        "目前进度",
        listOf(
            "所有" to "",
            "連載中" to "0",
            "已完結" to "1",
        ),
    )

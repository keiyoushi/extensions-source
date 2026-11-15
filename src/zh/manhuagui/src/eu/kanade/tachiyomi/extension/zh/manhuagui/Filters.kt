package eu.kanade.tachiyomi.extension.zh.manhuagui

import eu.kanade.tachiyomi.source.model.Filter

internal open class UriPartFilter(
    displayName: String,
    val pair: Array<Pair<String, String>>,
    defaultState: Int = 0,
) : Filter.Select<String>(displayName, pair.map { it.first }.toTypedArray(), defaultState) {
    open fun toUriPart() = pair[state].second
}

internal class SortFilter :
    UriPartFilter(
        "排序方式",
        arrayOf(
            Pair("人气最旺", "view"),
            Pair("最新发布", ""),
            Pair("最新更新", "update"),
            Pair("评分最高", "rate"),
            Pair("日排行", Manhuagui.RANK_PREFIX),
            Pair("周排行", "${Manhuagui.RANK_PREFIX}week"),
            Pair("月排行", "${Manhuagui.RANK_PREFIX}month"),
            Pair("总排行", "${Manhuagui.RANK_PREFIX}total"),
        ),
    )

internal class LocaleFilter :
    UriPartFilter(
        "按地区",
        arrayOf(
            Pair("全部", ""),
            Pair("日本", "japan"),
            Pair("港台", "hongkong"),
            Pair("其它", "other"),
            Pair("欧美", "europe"),
            Pair("内地", "china"),
            Pair("韩国", "korea"),
        ),
    )

internal class GenreFilter :
    UriPartFilter(
        "按剧情",
        arrayOf(
            Pair("全部", ""),
            Pair("热血", "rexue"),
            Pair("冒险", "maoxian"),
            Pair("魔幻", "mohuan"),
            Pair("神鬼", "shengui"),
            Pair("搞笑", "gaoxiao"),
            Pair("萌系", "mengxi"),
            Pair("爱情", "aiqing"),
            Pair("科幻", "kehuan"),
            Pair("魔法", "mofa"),
            Pair("格斗", "gedou"),
            Pair("武侠", "wuxia"),
            Pair("机战", "jizhan"),
            Pair("战争", "zhanzheng"),
            Pair("竞技", "jingji"),
            Pair("体育", "tiyu"),
            Pair("校园", "xiaoyuan"),
            Pair("生活", "shenghuo"),
            Pair("励志", "lizhi"),
            Pair("历史", "lishi"),
            Pair("伪娘", "weiniang"),
            Pair("宅男", "zhainan"),
            Pair("腐女", "funv"),
            Pair("耽美", "danmei"),
            Pair("百合", "baihe"),
            Pair("后宫", "hougong"),
            Pair("治愈", "zhiyu"),
            Pair("美食", "meishi"),
            Pair("推理", "tuili"),
            Pair("悬疑", "xuanyi"),
            Pair("恐怖", "kongbu"),
            Pair("四格", "sige"),
            Pair("职场", "zhichang"),
            Pair("侦探", "zhentan"),
            Pair("社会", "shehui"),
            Pair("音乐", "yinyue"),
            Pair("舞蹈", "wudao"),
            Pair("杂志", "zazhi"),
            Pair("黑道", "heidao"),
        ),
    )

internal class ReaderFilter :
    UriPartFilter(
        "按受众",
        arrayOf(
            Pair("全部", ""),
            Pair("少女", "shaonv"),
            Pair("少年", "shaonian"),
            Pair("青年", "qingnian"),
            Pair("儿童", "ertong"),
            Pair("通用", "tongyong"),
        ),
    )

internal class PublishDateFilter :
    UriPartFilter(
        "按年份",
        arrayOf(
            Pair("全部", ""),
            Pair("2025年", "2025"),
            Pair("2024年", "2024"),
            Pair("2023年", "2023"),
            Pair("2022年", "2022"),
            Pair("2021年", "2021"),
            Pair("2020年", "2020"),
            Pair("2019年", "2019"),
            Pair("2018年", "2018"),
            Pair("2017年", "2017"),
            Pair("2016年", "2016"),
            Pair("2015年", "2015"),
            Pair("2014年", "2014"),
            Pair("2013年", "2013"),
            Pair("2012年", "2012"),
            Pair("2011年", "2011"),
            Pair("2010年", "2010"),
            Pair("00年代", "200x"),
            Pair("90年代", "199x"),
            Pair("80年代", "198x"),
            Pair("更早", "197x"),
        ),
    )

internal class FirstLetterFilter :
    UriPartFilter(
        "按字母",
        arrayOf(
            Pair("全部", ""),
            Pair("A", "a"),
            Pair("B", "b"),
            Pair("C", "c"),
            Pair("D", "d"),
            Pair("E", "e"),
            Pair("F", "f"),
            Pair("G", "g"),
            Pair("H", "h"),
            Pair("I", "i"),
            Pair("J", "j"),
            Pair("K", "k"),
            Pair("L", "l"),
            Pair("M", "m"),
            Pair("N", "n"),
            Pair("O", "o"),
            Pair("P", "p"),
            Pair("Q", "q"),
            Pair("R", "r"),
            Pair("S", "s"),
            Pair("T", "t"),
            Pair("U", "u"),
            Pair("V", "v"),
            Pair("W", "w"),
            Pair("X", "x"),
            Pair("Y", "y"),
            Pair("Z", "z"),
            Pair("0-9", "0-9"),
        ),
    )

internal class StatusFilter :
    UriPartFilter(
        "按进度",
        arrayOf(
            Pair("全部", ""),
            Pair("连载", "lianzai"),
            Pair("完结", "wanjie"),
        ),
    )

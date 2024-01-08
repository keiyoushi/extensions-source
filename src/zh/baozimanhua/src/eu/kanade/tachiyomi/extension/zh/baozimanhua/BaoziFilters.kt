package eu.kanade.tachiyomi.extension.zh.baozimanhua

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters() = FilterList(
    Filter.Header("注意：不影響按標題搜索"),
    TagFilter(),
    RegionFilter(),
    StatusFilter(),
    StartFilter(),
)

open class UriPartFilter(name: String, private val query: String, private val vals: Array<Pair<String, String>>) :
    Filter.Select<String>(name, vals.map { it.first }.toTypedArray()) {
    fun toUriPart() = "$query=${vals[state].second}"
}

private class TagFilter : UriPartFilter(
    "标签",
    "type",
    arrayOf(
        Pair("全部", "all"),
        Pair("都市", "dushi"),
        Pair("冒险", "mouxian"),
        Pair("热血", "rexie"),
        Pair("恋爱", "lianai"),
        Pair("耽美", "danmei"),
        Pair("武侠", "wuxia"),
        Pair("格斗", "gedou"),
        Pair("科幻", "kehuan"),
        Pair("魔幻", "mohuan"),
        Pair("推理", "tuili"),
        Pair("玄幻", "xuanhuan"),
        Pair("日常", "richang"),
        Pair("生活", "shenghuo"),
        Pair("搞笑", "gaoxiao"),
        Pair("校园", "xiaoyuan"),
        Pair("奇幻", "qihuan"),
        Pair("萌系", "mengxi"),
        Pair("穿越", "chuanyue"),
        Pair("后宫", "hougong"),
        Pair("战争", "zhanzheng"),
        Pair("历史", "lishi"),
        Pair("剧情", "juqing"),
        Pair("同人", "tongren"),
        Pair("竞技", "jingji"),
        Pair("励志", "lizhi"),
        Pair("治愈", "zhiyu"),
        Pair("机甲", "jijia"),
        Pair("纯爱", "chunai"),
        Pair("美食", "meishi"),
        Pair("恶搞", "egao"),
        Pair("虐心", "nuexin"),
        Pair("动作", "dongzuo"),
        Pair("惊险", "liangxian"),
        Pair("唯美", "weimei"),
        Pair("复仇", "fuchou"),
        Pair("脑洞", "naodong"),
        Pair("宫斗", "gongdou"),
        Pair("运动", "yundong"),
        Pair("灵异", "lingyi"),
        Pair("古风", "gufeng"),
        Pair("权谋", "quanmou"),
        Pair("节操", "jiecao"),
        Pair("明星", "mingxing"),
        Pair("暗黑", "anhei"),
        Pair("社会", "shehui"),
        Pair("音乐舞蹈", "yinlewudao"),
        Pair("东方", "dongfang"),
        Pair("AA", "aa"),
        Pair("悬疑", "xuanyi"),
        Pair("轻小说", "qingxiaoshuo"),
        Pair("霸总", "bazong"),
        Pair("萝莉", "luoli"),
        Pair("战斗", "zhandou"),
        Pair("惊悚", "liangsong"),
        Pair("百合", "yuri"),
        Pair("大女主", "danuzhu"),
        Pair("幻想", "huanxiang"),
        Pair("少女", "shaonu"),
        Pair("少年", "shaonian"),
        Pair("性转", "xingzhuanhuan"),
        Pair("重生", "zhongsheng"),
        Pair("韩漫", "hanman"),
        Pair("其它", "qita"),
    ),
)

private class RegionFilter : UriPartFilter(
    "地区",
    "region",
    arrayOf(
        Pair("全部", "all"),
        Pair("国漫", "cn"),
        Pair("日本", "jp"),
        Pair("韩国", "kr"),
        Pair("欧美", "en"),
    ),
)

private class StatusFilter : UriPartFilter(
    "进度",
    "state",
    arrayOf(
        Pair("全部", "all"),
        Pair("连载中", "serial"),
        Pair("已完结", "pub"),
    ),
)

private class StartFilter : UriPartFilter(
    "标题开头",
    "filter",
    arrayOf(
        Pair("全部", "*"),
        Pair("ABCD", "ABCD"),
        Pair("EFGH", "EFGH"),
        Pair("IJKL", "IJKL"),
        Pair("MNOP", "MNOP"),
        Pair("QRST", "QRST"),
        Pair("UVW", "UVW"),
        Pair("XYZ", "XYZ"),
        Pair("0-9", "0-9"),
    ),
)

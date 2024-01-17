package eu.kanade.tachiyomi.extension.zh.onemanhua

import eu.kanade.tachiyomi.multisrc.colamanga.ColaManga
import eu.kanade.tachiyomi.source.model.FilterList

// Originally, the site was called One漫画. The name has been changing every once in awhile
class Onemanhua : ColaManga(
    "COLAMANGA",
    "https://www.colamanga.com",
    "zh",
) {
    override val id = 8252565807829914103 // name used to be "One漫画"

    override fun popularMangaNextPageSelector() = "a:contains(下页):not(.fed-btns-disad)"

    override val statusTitle = "状态"

    override val authorTitle = "作者"

    override val genreTitle = "类别"

    override val statusOngoing = "连载中"

    override val statusCompleted = "已完结"

    private class StatusFilter : QueryFilter(
        "状态",
        "status",
        arrayOf(
            Pair("全部", ""),
            Pair("连载中", "1"),
            Pair("已完结", "2"),
        ),
    )

    private class SortFilter : QueryFilter(
        "排序",
        "orderBy",
        arrayOf(
            Pair("更新日", "update"),
            Pair("日点击", "dailyCount"),
            Pair("周点击", "weeklyCount"),
            Pair("月点击", "monthlyCount"),
        ),
        1,
    )

    private class CategoryFilter : QueryFilter(
        "类型",
        "mainCategoryId",
        arrayOf(
            Pair("全部", ""),
            Pair("热血", "10023"),
            Pair("玄幻", "10024"),
            Pair("恋爱", "10126"),
            Pair("冒险", "10210"),
            Pair("古风", "10143"),
            Pair("都市", "10124"),
            Pair("穿越", "10129"),
            Pair("奇幻", "10242"),
            Pair("其他", "10560"),
            Pair("少男", "10641"),
            Pair("搞笑", "10122"),
            Pair("战斗", "10309"),
            Pair("冒险热血", "11224"),
            Pair("重生", "10461"),
            Pair("爆笑", "10201"),
            Pair("逆袭", "10943"),
            Pair("后宫", "10138"),
            Pair("少年", "10321"),
            Pair("少女", "10301"),
            Pair("熱血", "12044"),
            Pair("系统", "10722"),
            Pair("动作", "10125"),
            Pair("校园", "10131"),
            Pair("冒險", "12123"),
            Pair("修真", "10133"),
            Pair("修仙", "10453"),
            Pair("剧情", "10480"),
            Pair("霸总", "10127"),
            Pair("大女主", "10706"),
            Pair("生活", "10142"),
        ),
    )

    private class CharFilter : QueryFilter(
        "字母",
        "charCategoryId",
        arrayOf(
            Pair("全部", ""),
            Pair("A", "10182"),
            Pair("B", "10081"),
            Pair("C", "10134"),
            Pair("D", "10001"),
            Pair("E", "10238"),
            Pair("F", "10161"),
            Pair("G", "10225"),
            Pair("H", "10137"),
            Pair("I", "10284"),
            Pair("J", "10141"),
            Pair("K", "10283"),
            Pair("L", "10132"),
            Pair("M", "10136"),
            Pair("N", "10130"),
            Pair("O", "10282"),
            Pair("P", "10262"),
            Pair("Q", "10164"),
            Pair("R", "10240"),
            Pair("S", "10121"),
            Pair("T", "10123"),
            Pair("U", "11184"),
            Pair("V", "11483"),
            Pair("W", "10135"),
            Pair("X", "10061"),
            Pair("Y", "10082"),
            Pair("Z", "10128"),
        ),
    )

    override fun getFilterList() = FilterList(
        SortFilter(),
        CategoryFilter(),
        CharFilter(),
        StatusFilter(),
    )

    override val logTag = "onemanhua"

    /** main site's connection limit */
    override val mainSiteRateLimitPrefTitle = "主站连接限制"

    /** This value affects connection request amount to main site. Lowering this value may reduce the chance to get HTTP 403 error, but loading speed will be slower too. Tachiyomi restart required. Current value: %s" */
    override val mainSiteRateLimitPrefSummary = "此值影响主站的连接请求量。降低此值可以减少获得HTTP 403错误的几率，但加载速度也会变慢。需要重启软件以生效。\n默认值：$MAINSITE_RATEPERMITS_PREF_DEFAULT \n当前值：%s"

    /** main site's connection limit period */
    override val mainSiteRateLimitPeriodPrefTitle = "主站连接限制期"

    /** This value affects the delay when hitting the connection limit to main site. Increasing this value may reduce the chance to get HTTP 403 error, but loading speed will be slower too. Tachiyomi restart required. Current value: %s" */
    override val mainSiteRateLimitPeriodPrefSummary = "此值影响主站点连接限制时的延迟（毫秒）。增加这个值可能会减少出现HTTP 403错误的机会，但加载速度也会变慢。需要重启软件以生效。\n默认值：$MAINSITE_RATEPERIOD_PREF_DEFAULT\n当前值：%s"
}

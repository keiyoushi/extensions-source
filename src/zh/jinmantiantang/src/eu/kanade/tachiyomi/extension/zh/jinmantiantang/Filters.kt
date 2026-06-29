package eu.kanade.tachiyomi.extension.zh.jinmantiantang

import eu.kanade.tachiyomi.source.model.Filter

internal class CategoryGroup :
    UriPartFilter(
        "按类型",
        arrayOf(
            Pair("全部", "/albums?"),
            Pair("其他", "/albums/another?"),
            Pair("同人", "/albums/doujin?"),
            Pair("韩漫", "/albums/hanman?"),
            Pair("美漫", "/albums/meiman?"),
            Pair("短篇", "/albums/short?"),
            Pair("单本", "/albums/single?"),
            Pair("汉化", "/albums/doujin/sub/chinese?"),
            Pair("日语", "/albums/doujin/sub/japanese?"),
            Pair("汉化", "/albums/doujin/sub/chinese?"),
            Pair("Cosplay", "/albums/doujin/sub/cosplay?"),
            Pair("CG图集", "/albums/doujin/sub/CG?"),

            Pair("P站", "/search/photos?search_query=PIXIV&"),
            Pair("3D", "/search/photos?search_query=3D&"),

            Pair("剧情", "/search/photos?search_query=劇情&"),
            Pair("校园", "/search/photos?search_query=校園&"),
            Pair("纯爱", "/search/photos?search_query=純愛&"),
            Pair("人妻", "/search/photos?search_query=人妻&"),
            Pair("师生", "/search/photos?search_query=師生&"),
            Pair("乱伦", "/search/photos?search_query=亂倫&"),
            Pair("近亲", "/search/photos?search_query=近親&"),
            Pair("百合", "/search/photos?search_query=百合&"),
            Pair("男同", "/search/photos?search_query=YAOI&"),
            Pair("性转", "/search/photos?search_query=性轉&"),
            Pair("NTR", "/search/photos?search_query=NTR&"),
            Pair("伪娘", "/search/photos?search_query=偽娘&"),
            Pair("痴女", "/search/photos?search_query=癡女&"),
            Pair("全彩", "/search/photos?search_query=全彩&"),
            Pair("女性向", "/search/photos?search_query=女性向&"),

            Pair("萝莉", "/search/photos?search_query=蘿莉&"),
            Pair("御姐", "/search/photos?search_query=御姐&"),
            Pair("熟女", "/search/photos?search_query=熟女&"),
            Pair("正太", "/search/photos?search_query=正太&"),
            Pair("巨乳", "/search/photos?search_query=巨乳&"),
            Pair("贫乳", "/search/photos?search_query=貧乳&"),
            Pair("女王", "/search/photos?search_query=女王&"),
            Pair("教师", "/search/photos?search_query=教師&"),
            Pair("女仆", "/search/photos?search_query=女僕&"),
            Pair("护士", "/search/photos?search_query=護士&"),
            Pair("泳裝", "/search/photos?search_query=泳裝&"),
            Pair("眼镜", "/search/photos?search_query=眼鏡&"),
            Pair("丝袜", "/search/photos?search_query=絲襪&"),
            Pair("连裤袜", "/search/photos?search_query=連褲襪&"),
            Pair("制服", "/search/photos?search_query=制服&"),
            Pair("兔女郎", "/search/photos?search_query=兔女郎&"),

            Pair("群交", "/search/photos?search_query=群交&"),
            Pair("足交", "/search/photos?search_query=足交&"),
            Pair("SM", "/search/photos?search_query=SM&"),
            Pair("肛交", "/search/photos?search_query=肛交&"),
            Pair("阿黑颜", "/search/photos?search_query=阿黑顏&"),
            Pair("药物", "/search/photos?search_query=藥物&"),
            Pair("扶他", "/search/photos?search_query=扶他&"),
            Pair("调教", "/search/photos?search_query=調教&"),
            Pair("野外", "/search/photos?search_query=野外&"),
            Pair("露出", "/search/photos?search_query=露出&"),
            Pair("催眠", "/search/photos?search_query=催眠&"),
            Pair("自慰", "/search/photos?search_query=自慰&"),
            Pair("触手", "/search/photos?search_query=觸手&"),
            Pair("兽交", "/search/photos?search_query=獸交&"),
            Pair("亚人", "/search/photos?search_query=亞人&"),
            Pair("魔物", "/search/photos?search_query=魔物&"),

            Pair("CG集", "/search/photos?search_query=CG集&"),
            Pair("重口", "/search/photos?search_query=重口&"),
            Pair("猎奇", "/search/photos?search_query=獵奇&"),
            Pair("非H", "/search/photos?search_query=非H&"),
            Pair("血腥", "/search/photos?search_query=血腥&"),
            Pair("暴力", "/search/photos?search_query=暴力&"),
            Pair("血腥暴力", "/search/photos?search_query=血腥暴力&"),
        ),
    )

internal class SortFilter :
    UriPartFilter(
        "排序",
        arrayOf(
            Pair("最新", "o=mr&"),
            Pair("最多浏览", "o=mv&"),
            Pair("最多爱心", "o=tf&"),
            Pair("最多图片", "o=mp&"),
        ),
    )

internal class TimeFilter :
    UriPartFilter(
        "时间",
        arrayOf(
            Pair("全部", "t=a&"),
            Pair("今天", "t=t&"),
            Pair("这周", "t=w&"),
            Pair("本月", "t=m&"),
        ),
    )

internal class TypeFilter :
    UriPartFilter(
        "搜索范围",
        arrayOf(
            Pair("站内搜索", "main_tag=0"),
            Pair("作品", "main_tag=1"),
            Pair("作者", "main_tag=2"),
            Pair("标签", "main_tag=3"),
            Pair("登场人物", "main_tag=4"),
        ),
    )

/**
 *创建选择过滤器的类。 下拉菜单中的每个条目都有一个名称和一个显示名称。
 *如果选择了一个条目，它将作为查询参数附加到URI的末尾。
 *如果将firstIsUnspecified设置为true，则如果选择了第一个条目，则URI不会附加任何内容。
 */
// vals: <name, display>
internal open class UriPartFilter(
    displayName: String,
    val vals: Array<Pair<String, String>>,
    defaultValue: Int = 0,
) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray(), defaultValue) {
    open fun toUriPart() = vals[state].second
}

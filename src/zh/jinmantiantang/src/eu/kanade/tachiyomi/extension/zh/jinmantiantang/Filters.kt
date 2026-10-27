package eu.kanade.tachiyomi.extension.zh.jinmantiantang

import eu.kanade.tachiyomi.source.model.Filter

internal class CategoryGroup :
    UriPartFilter(
        "按类型",
        arrayOf(
            "全部" to "/albums?",
            "其他" to "/albums/another?",
            "同人" to "/albums/doujin?",
            "韩漫" to "/albums/hanman?",
            "美漫" to "/albums/meiman?",
            "短篇" to "/albums/short?",
            "单本" to "/albums/single?",
            "汉化" to "/albums/doujin/sub/chinese?",
            "日语" to "/albums/doujin/sub/japanese?",
            "汉化" to "/albums/doujin/sub/chinese?",
            "Cosplay" to "/albums/doujin/sub/cosplay?",
            "CG图集" to "/albums/doujin/sub/CG?",

            "P站" to "/search/photos?search_query=PIXIV&",
            "3D" to "/search/photos?search_query=3D&",

            "剧情" to "/search/photos?search_query=劇情&",
            "校园" to "/search/photos?search_query=校園&",
            "纯爱" to "/search/photos?search_query=純愛&",
            "人妻" to "/search/photos?search_query=人妻&",
            "师生" to "/search/photos?search_query=師生&",
            "乱伦" to "/search/photos?search_query=亂倫&",
            "近亲" to "/search/photos?search_query=近親&",
            "百合" to "/search/photos?search_query=百合&",
            "男同" to "/search/photos?search_query=YAOI&",
            "性转" to "/search/photos?search_query=性轉&",
            "NTR" to "/search/photos?search_query=NTR&",
            "伪娘" to "/search/photos?search_query=偽娘&",
            "痴女" to "/search/photos?search_query=癡女&",
            "全彩" to "/search/photos?search_query=全彩&",
            "女性向" to "/search/photos?search_query=女性向&",

            "萝莉" to "/search/photos?search_query=蘿莉&",
            "御姐" to "/search/photos?search_query=御姐&",
            "熟女" to "/search/photos?search_query=熟女&",
            "正太" to "/search/photos?search_query=正太&",
            "巨乳" to "/search/photos?search_query=巨乳&",
            "贫乳" to "/search/photos?search_query=貧乳&",
            "女王" to "/search/photos?search_query=女王&",
            "教师" to "/search/photos?search_query=教師&",
            "女仆" to "/search/photos?search_query=女僕&",
            "护士" to "/search/photos?search_query=護士&",
            "泳裝" to "/search/photos?search_query=泳裝&",
            "眼镜" to "/search/photos?search_query=眼鏡&",
            "丝袜" to "/search/photos?search_query=絲襪&",
            "连裤袜" to "/search/photos?search_query=連褲襪&",
            "制服" to "/search/photos?search_query=制服&",
            "兔女郎" to "/search/photos?search_query=兔女郎&",

            "群交" to "/search/photos?search_query=群交&",
            "足交" to "/search/photos?search_query=足交&",
            "SM" to "/search/photos?search_query=SM&",
            "肛交" to "/search/photos?search_query=肛交&",
            "阿黑颜" to "/search/photos?search_query=阿黑顏&",
            "药物" to "/search/photos?search_query=藥物&",
            "扶他" to "/search/photos?search_query=扶他&",
            "调教" to "/search/photos?search_query=調教&",
            "野外" to "/search/photos?search_query=野外&",
            "露出" to "/search/photos?search_query=露出&",
            "催眠" to "/search/photos?search_query=催眠&",
            "自慰" to "/search/photos?search_query=自慰&",
            "触手" to "/search/photos?search_query=觸手&",
            "兽交" to "/search/photos?search_query=獸交&",
            "亚人" to "/search/photos?search_query=亞人&",
            "魔物" to "/search/photos?search_query=魔物&",

            "CG集" to "/search/photos?search_query=CG集&",
            "重口" to "/search/photos?search_query=重口&",
            "猎奇" to "/search/photos?search_query=獵奇&",
            "非H" to "/search/photos?search_query=非H&",
            "血腥" to "/search/photos?search_query=血腥&",
            "暴力" to "/search/photos?search_query=暴力&",
            "血腥暴力" to "/search/photos?search_query=血腥暴力&",
        ),
    )

internal class SortFilter :
    UriPartFilter(
        "排序",
        arrayOf(
            "最新" to "o=mr&",
            "最多浏览" to "o=mv&",
            "最多爱心" to "o=tf&",
            "最多图片" to "o=mp&",
        ),
    )

internal class TimeFilter :
    UriPartFilter(
        "时间",
        arrayOf(
            "全部" to "t=a&",
            "今天" to "t=t&",
            "这周" to "t=w&",
            "本月" to "t=m&",
        ),
    )

internal class TypeFilter :
    UriPartFilter(
        "搜索范围",
        arrayOf(
            "站内搜索" to "main_tag=0",
            "作品" to "main_tag=1",
            "作者" to "main_tag=2",
            "标签" to "main_tag=3",
            "登场人物" to "main_tag=4",
        ),
    )

internal class FavoritesFilter : Filter.Select<String>("收藏夹", arrayOf("关闭", "启用"), 0) {
    fun isEnabled() = state == 1
}

internal open class UriPartFilter(
    displayName: String,
    val vals: Array<Pair<String, String>>,
    defaultValue: Int = 0,
) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray(), defaultValue) {
    open fun toUriPart() = vals[state].second
}

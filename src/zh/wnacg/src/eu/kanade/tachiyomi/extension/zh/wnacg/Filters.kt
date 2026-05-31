package eu.kanade.tachiyomi.extension.zh.wnacg

import eu.kanade.tachiyomi.source.model.Filter

class TagFilter : Filter.Text(name = "标签")

class CategoryFilter :
    UriPartFilter(
        "分类",
        arrayOf(
            Pair("", ""),
            Pair("更新", "albums-index-page-%d.html"),
            Pair("同人志", "albums-index-page-%d-cate-5.html"),
            Pair("同人志-汉化", "albums-index-page-%d-cate-1.html"),
            Pair("同人志-日语", "albums-index-page-%d-cate-12.html"),
            Pair("同人志-English（英语）", "albums-index-cate-16.html"),
            Pair("同人志-CG书籍", "albums-index-page-%d-cate-2.html"),
            Pair("写真&Cosplay", "albums-index-page-%d-cate-3.html"),
            Pair("单行本", "albums-index-page-%d-cate-6.html"),
            Pair("单行本-汉化", "albums-index-page-%d-cate-9.html"),
            Pair("单行本-English（英语）", "albums-index-page-%d-cate-17.html"),
            Pair("单行本-日语", "albums-index-page-%d-cate-13.html"),
            Pair("杂志&短篇-汉语", "albums-index-page-%d-cate-7.html"),
            Pair("杂志&短篇-汉语", "albums-index-page-%d-cate-10.html"),
            Pair("杂志&短篇-日语", "albums-index-page-%d-cate-14.html"),
            Pair("杂志&短篇-English（英语）", "albums-index-cate-18.html"),
            Pair("韩漫", "albums-index-page-%d-cate-19.html"),
            Pair("韩漫-汉化", "albums-index-page-%d-cate-20.html"),
            Pair("韩漫-生肉", "albums-index-page-%d-cate-21.html"),
            Pair("3D&漫画", "albums-index-page-%d-cate-22.html"),
            Pair("3D&漫画-汉语", "albums-index-page-%d-cate-23.html"),
            Pair("3D&漫画-其他", "albums-index-page-%d-cate-24.html"),
            Pair("AI图集", "albums-index-page-%d-cate-37.html"),
            Pair("未分類相冊", "albums-index-page-%d-cate-0.html"),
        ),
    )

open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart() = vals[state].second
}

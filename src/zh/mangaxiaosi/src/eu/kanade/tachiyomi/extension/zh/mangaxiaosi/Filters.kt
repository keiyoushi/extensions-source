package eu.kanade.tachiyomi.extension.zh.mangaxiaosi

import eu.kanade.tachiyomi.source.model.Filter

open class UriPartFilter(
    displayName: String,
    private val vals: Array<Pair<String, String>>,
) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun selectedValue() = vals[state].second
}

class GenreFilter :
    UriPartFilter(
        "题材",
        arrayOf(
            Pair("全部", "全部"),
            Pair("青春", "青春"),
            Pair("性感", "性感"),
            Pair("长腿", "长腿"),
            Pair("多人", "多人"),
            Pair("御姐", "御姐"),
            Pair("巨乳", "巨乳"),
            Pair("新婚", "新婚"),
            Pair("媳妇", "媳妇"),
            Pair("暧昧", "暧昧"),
            Pair("清纯", "清纯"),
            Pair("调教", "调教"),
            Pair("少妇", "少妇"),
            Pair("风骚", "风骚"),
            Pair("同居", "同居"),
            Pair("淫乱", "淫乱"),
            Pair("好友", "好友"),
            Pair("女神", "女神"),
            Pair("诱惑", "诱惑"),
            Pair("偷情", "偷情"),
            Pair("出轨", "出轨"),
            Pair("正妹", "正妹"),
            Pair("家教", "家教"),
        ),
    )

class AreaFilter :
    UriPartFilter(
        "地区",
        arrayOf(
            Pair("全部", "-1"),
            Pair("韩国", "1"),
            Pair("日本", "2"),
            Pair("台湾", "3"),
        ),
    )

class StatusFilter :
    UriPartFilter(
        "进度",
        arrayOf(
            Pair("全部", "-1"),
            Pair("连载", "0"),
            Pair("完结", "1"),
        ),
    )

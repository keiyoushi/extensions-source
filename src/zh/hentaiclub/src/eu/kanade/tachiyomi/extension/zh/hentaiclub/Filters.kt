package eu.kanade.tachiyomi.extension.zh.hentaiclub

import eu.kanade.tachiyomi.source.model.Filter

class SortFilter :
    Filter.Select<String>(
        "分类",
        arrayOf("全部", "R-15", "R-18"),
    ) {
    fun getValue(): String = when (state) {
        1 -> "r15"
        2 -> "r18"
        else -> ""
    }
}

class TagFilter : Filter.Text("标签 (输入标签名)")

package eu.kanade.tachiyomi.extension.zh.manwa

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl.Builder

internal open class UriPartFilter(
    displayName: String,
    val vals: List<List<String>>,
    defaultValue: Int = 0,
) : Filter.Select<String>(displayName, vals.map { it[0] }.toTypedArray(), defaultValue) {
    open fun setParamPair(builder: Builder) {
        builder.setQueryParameter(vals[state][1], vals[state][2])
    }
}

internal class EndFilter :
    UriPartFilter(
        "状态",
        listOf(
            listOf("全部", "end=", ""),
            listOf("连载中", "end", "2"),
            listOf("完结", "end", "1"),
        ),
    )

internal class CGenderFilter :
    UriPartFilter(
        "类型",
        listOf(
            listOf("全部", "gender", "-1"),
            listOf("一般向", "gender", "2"),
            listOf("BL向", "gender", "0"),
            listOf("禁漫", "gender", "1"),
            listOf("TL向", "gender", "3"),
        ),
    )

internal class AreaFilter :
    UriPartFilter(
        "地区",
        listOf(
            listOf("全部", "area", ""),
            listOf("韩国", "area", "2"),
            listOf("日漫", "area", "3"),
            listOf("国漫", "area", "4"),
            listOf("台漫", "area", "5"),
            listOf("其他", "area", "6"),
            listOf("未分类", "area", "1"),
        ),
    )

internal class SortFilter :
    UriPartFilter(
        "排序",
        listOf(
            listOf("最新", "sort", "-1"),
            listOf("最旧", "sort", "0"),
            listOf("收藏", "sort", "1"),
            listOf("新漫", "sort", "2"),
        ),
    )

internal class TagCheckBoxFilter(name: String, val key: String) : Filter.CheckBox(name) {
    override fun toString(): String = key
}

internal class TagCheckBoxFilterGroup(
    name: String,
    data: LinkedHashMap<String, String>,
) : Filter.Group<TagCheckBoxFilter>(
    name,
    data.map { (k, v) ->
        TagCheckBoxFilter(k, v)
    },
) {
    fun setParamPair(builder: Builder) {
        if (state[0].state) {
            // clear
            state.forEach { it.state = false }
            builder.setQueryParameter("tag", null)
            return
        }
        builder.setQueryParameter("tag", state.filter { it.state }.joinToString { it.toString() })
    }
}

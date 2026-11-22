package eu.kanade.tachiyomi.extension.zh.bilibilimanga

import eu.kanade.tachiyomi.source.model.Filter

open class LabelFilter(name: String, private val labels: List<LabelDto>) : Filter.Select<String>(name, labels.map { it.name }.toTypedArray()) {
    val id get() = labels[state].id
}

class StyleFilter(labels: List<LabelDto>) : LabelFilter("题材", listOf(ALL) + labels)

class AreaFilter(labels: List<LabelDto>) : LabelFilter("地区", listOf(ALL) + labels)

class StatusFilter(labels: List<LabelDto>) : LabelFilter("进度", listOf(ALL) + labels)

class PriceFilter(labels: List<LabelDto>) : LabelFilter("收费", listOf(ALL) + labels)

class SpecialFilter(labels: List<LabelDto>) : LabelFilter("特色", listOf(LabelDto(0, "全部")) + labels)

class OrderFilter(labels: List<LabelDto>) : LabelFilter("排序", labels)

private val ALL = LabelDto(-1, "全部")

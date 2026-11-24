package eu.kanade.tachiyomi.extension.th.mangathnet

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

open class SelectFilter(name: String, private val options: List<Pair<String, String?>>) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    val selected get() = options[state].second
}

class TypeFilter : SelectFilter("ประเภท", TYPES)

class StatusFilter : SelectFilter("สถานะ", STATUSES)

class SortFilter : SelectFilter("เรียงตาม", SORTS)

class TagCheckBox(name: String) : Filter.CheckBox(name)

class TagFilter(tags: List<String>) : Filter.Group<TagCheckBox>("แนว", tags.map(::TagCheckBox)) {
    val selected get() = state.filter { it.state }.map { it.name }
}

fun getFilters(tags: List<String>?) = FilterList(
    TypeFilter(),
    StatusFilter(),
    SortFilter(),
    tags?.let(::TagFilter) ?: Filter.Header("กด “รีเซ็ต” เพื่อโหลดแนว"),
)

private val TYPES = listOf(
    "ทุกประเภท" to null,
    "มังงะ" to "manga",
    "มันฮัว" to "manhua",
    "มันฮวา" to "manhwa",
    "โดจิน" to "doujin",
)

private val STATUSES = listOf(
    "ทั้งหมด" to null,
    "ยังไม่จบ" to "ongoing",
    "จบแล้ว" to "complete",
    "พักชั่วคราว" to "hiatus",
)

private val SORTS = listOf(
    "ยอดนิยม" to "popular",
    "อัปเดตล่าสุด" to "updated",
    "คะแนนสูงสุด" to "rating",
    "บุ๊กมาร์กมากสุด" to "bookmarked",
    "ชื่อเรื่อง ก-ฮ" to "title",
)

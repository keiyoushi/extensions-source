package eu.kanade.tachiyomi.extension.vi.suicaodex

import eu.kanade.tachiyomi.source.model.Filter

class StatusFilter :
    Filter.Select<String>(
        "Trạng thái",
        arrayOf("Tất cả", "Đang phát hành", "Hoàn thành", "Tạm ngưng", "Đã hủy"),
    ) {
    fun toApiValue(): String? = when (state) {
        1 -> "ongoing"
        2 -> "completed"
        3 -> "hiatus"
        4 -> "cancelled"
        else -> null
    }
}

class HasChapterFilter :
    Filter.Select<String>(
        "Chương",
        arrayOf("Tất cả", "Có chương", "Chưa có chương"),
        state = 1,
    ) {
    fun toApiValue(): String = when (state) {
        1 -> "true"
        2 -> "false"
        else -> "all"
    }
}

class SortFilter :
    Filter.Sort(
        "Sắp xếp theo",
        arrayOf("Chương mới nhất", "Ngày phát hành", "Tên truyện", "Năm"),
        state = Selection(0, ascending = false),
    ) {
    fun toApiValue(): String = when (state?.index) {
        1 -> "publishedAt"
        2 -> "title"
        3 -> "year"
        else -> "latestChapter"
    }

    fun direction(): String = if (state?.ascending == true) "asc" else "desc"
}

open class TagModeFilter(name: String) :
    Filter.Select<String>(
        name,
        arrayOf("Tất cả (AND)", "Ít nhất một (OR)"),
    ) {
    fun toApiValue(): String = if (state == 1) "or" else "and"
}

class TagTriStateFilter(val tagId: String, name: String) : Filter.TriState(name)

class TagGroupFilter(group: String, tags: List<TagDto>) :
    Filter.Group<TagTriStateFilter>(
        when (group) {
            "content" -> "Nội dung"
            "format" -> "Định dạng"
            "genre" -> "Thể loại"
            "theme" -> "Chủ đề"
            else -> group.replaceFirstChar { it.uppercase() }
        },
        tags.map { TagTriStateFilter(it.id, it.name) },
    )

open class TextUuidFilter(name: String) : Filter.Text(name) {
    fun ids(): List<String> = state.split(',', ' ', '\n')
        .map { it.trim() }
        .filter { it.matches(UUID_REGEX) }

    companion object {
        private val UUID_REGEX = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
    }
}

class AuthorFilter : TextUuidFilter("Tác giả, cách nhau bởi dấu phẩy)")
class ArtistFilter : TextUuidFilter("Họa sĩ, cách nhau bởi dấu phẩy)")

class YearFilter : Filter.Text("Năm phát hành") {
    fun toApiValue(): String? = state.trim().takeIf { it.matches(Regex("\\d{4}")) }
}

package eu.kanade.tachiyomi.extension.vi.tulinhtruyen

import eu.kanade.tachiyomi.source.model.Filter
import kotlinx.serialization.Serializable

@Serializable
class TagOption(val name: String, val group: String)

class SortFilter :
    Filter.Sort(
        "Sắp xếp theo",
        arrayOf(
            "Lượt xem nhiều nhất (Top View)",
            "Chương mới cập nhật",
            "Mới đăng gần đây",
            "Tên truyện (A-Z)",
        ),
        Selection(0, false),
    ) {
    val isDefault: Boolean get() = state?.let { it.index == 0 && !it.ascending } ?: true

    fun toQuery(): Pair<String, String> {
        val key = when (state?.index) {
            1 -> "latestUploadedChapter"
            2 -> "createdAt"
            3 -> "title"
            else -> "view_count"
        }
        return key to if (state?.ascending == true) "asc" else "desc"
    }
}

class StatusFilter :
    Filter.Group<StatusCheckBox>(
        "Trạng thái",
        listOf(
            StatusCheckBox("Đang tiến hành", "ongoing"),
            StatusCheckBox("Đã hoàn thành", "completed"),
            StatusCheckBox("Tạm ngưng", "hiatus"),
            StatusCheckBox("Đã hủy", "cancelled"),
        ),
    ) {
    fun selected(): List<String> = state.filter { it.state }.map { it.value }
}

class StatusCheckBox(name: String, val value: String) : Filter.CheckBox(name)

class OriginalLanguageFilter :
    Filter.Group<OriginalLanguageCheckBox>(
        "Xuất xứ",
        listOf(
            OriginalLanguageCheckBox("Manga (Nhật Bản)", "ja"),
            OriginalLanguageCheckBox("Manhwa (Hàn Quốc)", "ko"),
            OriginalLanguageCheckBox("Manhua (Trung Quốc)", "zh"),
            OriginalLanguageCheckBox("Truyện tranh Việt", "vi"),
        ),
    ) {
    fun selected(): List<String> = state.filter { it.state }.map { it.value }
}

class OriginalLanguageCheckBox(name: String, val value: String) : Filter.CheckBox(name)

class TagFilter(group: String, tags: List<TagOption>) :
    Filter.Group<TagCheckBox>(
        groupLabel(group),
        tags.map { TagCheckBox(it.name, it.name) },
    ) {
    fun selected(): List<String> = state.filter { it.state }.map { it.value }
}

class TagCheckBox(name: String, val value: String) : Filter.CheckBox(name)

private fun groupLabel(group: String): String = when (group) {
    "content" -> "Nội dung"
    "format" -> "Định dạng"
    "genre" -> "Thể loại"
    "theme" -> "Chủ đề"
    else -> group.replaceFirstChar { it.uppercase() }
}

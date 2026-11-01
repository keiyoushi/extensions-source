package eu.kanade.tachiyomi.extension.all.yaoime

import eu.kanade.tachiyomi.source.model.SChapter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.math.floor

@Serializable
class ChapterListDto(
    private val groups: List<ChapterGroupDto>,
    private val sources: Map<String, String>,
) {
    val anyVariant get() = groups.firstOrNull()?.variants?.firstOrNull()

    fun toSChapterList(lang: String, dedupe: Boolean, names: Map<String, String>): List<SChapter> {
        val inLang = groups.mapNotNull { group ->
            group.variants.filter { it.language == lang }
                .takeIf { it.isNotEmpty() }
                ?.let { group to it }
        }
        if (dedupe) {
            deduplicate(inLang, names).takeIf { it.isNotEmpty() }?.let { return it }
        }
        return inLang.flatMap { (group, variants) ->
            variants.map { it.toSChapter(group.label, group.number, group.createdAt, names) }
        }
    }

    // one entry per volume and whole chapter number; extras and unnumbered chapters dropped
    private fun deduplicate(inLang: List<Pair<ChapterGroupDto, List<VariantDto>>>, names: Map<String, String>): List<SChapter> {
        val buckets = inLang
            .filter { (group, variants) -> group.isNumbered && variants.any { it.d == 0 } }
            .groupBy { (group, _) -> "${group.vol ?: 0}:${group.wholeNumber}" }
            .values
        val volumesPerNumber = buckets.groupingBy { it.first().first.wholeNumber }.eachCount()

        return buckets.map { bucket ->
            val (first, variants) = bucket.minBy { it.first.num!! }
            val number = first.wholeNumber
            val vol = first.vol?.takeIf { volumesPerNumber[number]!! > 1 }
            val label = buildString {
                if (vol != null) append("Vol.", vol.toString().removeSuffix(".0"), " ")
                append("Ch.", number.toString().removeSuffix(".0"))
            }
            variants.first().toSChapter(label, number, bucket.maxOf { it.first.createdAt }, names)
        }
    }

    private fun VariantDto.toSChapter(label: String, number: Float, date: Long, names: Map<String, String>) = SChapter.create().apply {
        url = id.toString()
        name = label
        chapter_number = number
        date_upload = date
        scanlator = names[s.toString()] ?: sources[s.toString()]
        memo = buildJsonObject { put(MEMO_SOURCE, s) }
    }
}

@Serializable
class ChapterGroupDto(
    val label: String,
    val num: Float? = null,
    private val numReal: Int,
    val vol: Float? = null,
    val createdAt: Long,
    private val extra: Boolean = false,
    val variants: List<VariantDto>,
) {
    val isNumbered get() = !extra && numReal == 1 && num != null

    val wholeNumber get() = floor(num!!)

    val number get() = num?.takeIf { numReal == 1 } ?: -1f
}

@Serializable
class VariantDto(
    val id: Long,
    val s: Int,
    private val lang: String? = null,
    val d: Int = 0,
) {
    val language get() = lang ?: "en"
}

@Serializable
class ReaderDto(val chapter: ReaderChapterDto)

@Serializable
class ReaderChapterDto(
    val pages: List<ReaderPageDto>,
    val sealed: String? = null,
    val sources: Map<String, String> = emptyMap(),
)

@Serializable
class SeriesLdDto(
    @SerialName("@type") val type: String,
    val url: String? = null,
    val name: String? = null,
    val image: String? = null,
    val alternateName: List<String> = emptyList(),
)

@Serializable
class ReaderPageDto(
    val hash: String,
    val w: Int? = null,
)

@Serializable
class SealedPagesDto(val p: List<String>)

@Serializable
class FilterDataDto(
    val genres: List<NamedCountDto>,
    val tags: List<NamedCountDto>,
    val types: List<ValueCountDto>,
    val statuses: List<ValueCountDto>,
    val content: List<ValueCountDto>,
    val demographics: List<ValueCountDto>,
)

@Serializable
class NamedCountDto(val name: String)

@Serializable
class ValueCountDto(val value: String)

const val MEMO_SOURCE = "source"

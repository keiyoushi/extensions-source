package eu.kanade.tachiyomi.multisrc.manhuarm

import keiyoushi.utils.parseAs
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl

@Serializable
class PageFragment(
    val chapter: String,
    val index: Int,
    val dialogues: List<Dialog>,
)

// Page image urls carry their page in the fragment
fun HttpUrl.pageFragment(): PageFragment? = fragment?.takeIf { it.startsWith("{") }?.parseAs<PageFragment>()

@Serializable
data class Dialog(
    val x: Float,
    val y: Float,
    private val _width: Float,
    private val _height: Float,
    val angle: Float = 0f,
    val textByLanguage: Map<String, String> = emptyMap(),
) {
    var scale: Float = 1F
    val height: Float get() = scale * _height
    val width: Float get() = scale * _width

    val text: String get() = textByLanguage["text"] ?: throw Exception("Dialog not found")
    fun getTextBy(language: Language) = when {
        !language.disableTranslator -> textByLanguage[language.origin]
        else -> textByLanguage[language.target]
    } ?: text
    val centerY get() = height / 2 + y
    val centerX get() = width / 2 + x
}

@Serializable
class ReaderParamsDto(
    @SerialName("p") val payload: String,
    @SerialName("t") val timestamp: Long,
    @SerialName("n") val nonce: String,
    @SerialName("s") val seed: String,
    @SerialName("g") val signature: String,
    @SerialName("cs") val canvasSeed: Long,
    @SerialName("u") val endpoint: String,
)

@Serializable
class InitRequestDto(
    @SerialName("m") private val mode: String,
    @SerialName("p") private val payload: String,
)

@Serializable
class ImageRequestDto(
    @SerialName("m") private val mode: String,
    @SerialName("tk") private val ticket: String,
)

@Serializable
class SealedDto(
    @SerialName("u") val iv: String,
    @SerialName("v") val data: String,
    @SerialName("w") val tag: String,
)

@Serializable
class ChapterDataDto(
    @SerialName("d") val ocr: List<OcrPageDto>,
    @SerialName("m") val pages: List<ManifestEntryDto>,
)

@Serializable
class OcrPageDto(
    @SerialName("f") val token: String,
    @SerialName("b") val boxes: List<OcrBoxDto>,
)

@Serializable
class OcrBoxDto(
    @SerialName("r") private val box: List<Float>,
    @SerialName("c") private val text: String?,
) {
    fun toDialog(offset: IntArray) = Dialog(
        box[0] - offset[0],
        box[1] - offset[1],
        box[2] - offset[2],
        box[3] - offset[3],
        textByLanguage = mapOf("text" to text.orEmpty()),
    )
}

@Serializable
class ManifestEntryDto(
    @SerialName("i") val index: Int,
    @SerialName("k") val token: String,
    @SerialName("tk") val ticket: String,
    @SerialName("ik") val imageKey: String,
    @SerialName("is") val imageSeed: Long,
)

package eu.kanade.tachiyomi.extension.all.manhuarm

import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.Base64

class MachineTranslationsFactoryUtils

data class Language(
    val lang: String,
    val target: String = lang,
    val origin: String = "en",
    val fontSize: Int = 28,
    val dialogBoxScale: Float = 1f,
    val disableFontSettings: Boolean = false,
    val disableWordBreak: Boolean = false,
    val disableTranslator: Boolean = false,
    val supportNativeTranslation: Boolean = false,
    val fontName: String = "comic_neue_bold",
)

fun extractOcrUrl(script: String): String? {
    val ocrData = OCR_DATA_REGEX.find(script)?.groupValues?.get(1)?.parseAs<JsonObject>() ?: return null
    val baseUrl = ocrData.values.firstOrNull { it.jsonPrimitive.contentOrNull?.contains("fetch-ocr.php", ignoreCase = true) == true }?.jsonPrimitive?.content ?: return null

    return try {
        baseUrl.toHttpUrl().newBuilder().apply {
            PARAM_REGEX.findAll(script).forEach { match ->
                val paramName = match.groupValues[1]
                val decode = match.groupValues[2].isNotBlank()
                val dataKey = match.groupValues[3]

                ocrData[dataKey]?.jsonPrimitive?.contentOrNull?.let { value ->
                    addQueryParameter(paramName, if (decode) value.decodeBase64() else value)
                }
            }
        }.build().toString()
    } catch (_: Exception) {
        null
    }
}

private fun String.decodeBase64(): String = try {
    String(Base64.getDecoder().decode(this))
} catch (_: Exception) {
    this
}

private val OCR_DATA_REGEX = Regex("""\w+\s*=\s*(\{[^{}]*fetch-ocr\.php[^{}]*\})""")
private val PARAM_REGEX = Regex("""['"]?(\w+)['"]?\s*:\s*(atob\s*\(\s*)?\w+(?:\.|\[['"])(\w+)""")

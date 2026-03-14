package eu.kanade.tachiyomi.extension.all.manhuarm

import android.util.Base64
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl

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
    val cleanScript = script.replace("\\/", "/")

    val ocrData = OCR_DATA_REGEX.find(cleanScript)?.groupValues?.get(1)?.let {
        runCatching { it.parseAs<JsonObject>() }.getOrNull()
    }

    val vault = VAULT_REGEX.find(cleanScript)?.groupValues?.get(1)?.let { content ->
        VAULT_ITEM_REGEX.findAll(content).map { it.groupValues[1].ifEmpty { it.groupValues[2] } }.toList()
    } ?: emptyList()

    val baseUrl = vault.find { it.contains("fetch-ocr.php", true) }
        ?: ocrData?.values?.firstOrNull { it.jsonPrimitive.contentOrNull?.contains("fetch-ocr.php", true) == true }?.jsonPrimitive?.content
        ?: return null

    return runCatching {
        baseUrl.toHttpUrl().newBuilder().apply {
            PARAM_REGEX.findAll(cleanScript).forEach { match ->
                val (paramName, decodeMarker, dataKey) = match.destructured
                val value = ocrData?.get(dataKey)?.jsonPrimitive?.contentOrNull
                    ?: dataKey.toIntOrNull()?.let { vault.getOrNull(it) }

                value?.let {
                    val decodedValue = if (decodeMarker.isNotBlank()) it.decodeBase64() else it
                    addQueryParameter(paramName, decodedValue)
                }
            }
        }.build().toString()
    }.getOrNull()
}

private fun String.decodeBase64(): String = runCatching {
    String(Base64.decode(this, Base64.DEFAULT))
}.getOrDefault(this)

private val OCR_DATA_REGEX = Regex("""\w+\s*=\s*(\{[^{}]*fetch-ocr\.php[^{}]*\})""")
private val VAULT_REGEX = Regex("""\w+\s*=\s*(\[[^]]*fetch-ocr\.php[^]]*])""")
private val VAULT_ITEM_REGEX = Regex("""['"](.*?)['"]|(\d+)""")
private val PARAM_REGEX = Regex("""(?:append\(|['"]?)(\w{2,})['"]?\s*[:=,]\s*(atob\s*\()?\s*\w+(?:\.|\[['"]?)(\w+)""")

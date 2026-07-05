package eu.kanade.tachiyomi.extension.all.peppercarrot

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.decodeProtoBase64
import keiyoushi.utils.parseAs
import keiyoushi.utils.protoInstance
import kotlinx.serialization.encodeToByteArray
import okhttp3.Headers
import okhttp3.OkHttpClient
import org.jsoup.parser.Parser.unescapeEntities
import org.jsoup.select.Evaluator
import java.util.Locale

fun getPreferences(context: Context) = arrayOf(
    SwitchPreferenceCompat(context).apply {
        key = HI_RES_PREF
        title = "High resolution images"
        summary = "Changes will not be applied to images that are already cached or downloaded " +
            "until you clear the chapter cache or delete the chapter download."
        setDefaultValue(false)
    },
)

val SharedPreferences.isHiRes get() = getBoolean(HI_RES_PREF, false)
val SharedPreferences.lastUpdated get() = getLong(LAST_UPDATED_PREF, 0)

val SharedPreferences.lang: List<String>
    get() {
        val lang = getString(LANG_PREF, "")!!
        if (lang.isEmpty()) return emptyList()
        return lang.split(", ")
    }

fun SharedPreferences.Editor.setLang(value: Iterable<String>): SharedPreferences.Editor = putString(LANG_PREF, value.joinToString())

val SharedPreferences.langData: List<LangData>
    get() {
        val data = getString(LANG_DATA_PREF, "")!!
        if (data.isEmpty()) return emptyList()
        return data.decodeProtoBase64<List<LangData>>()
    }

@Synchronized
fun updateLangData(client: OkHttpClient, headers: Headers, preferences: SharedPreferences, baseUrl: String) {
    val lastUpdated = client.newCall(GET("$baseUrl/0_sources/last_updated.txt", headers))
        .execute().use { it.body.string().substringBefore('\n').toLong() }

    if (lastUpdated <= preferences.lastUpdated) return

    val editor = preferences.edit().putLong(LAST_UPDATED_PREF, lastUpdated)

    val episodes = client.newCall(GET("$baseUrl/0_sources/episodes.json", headers))
        .execute().parseAs<List<EpisodeDto>>()

    val total = episodes.size
    val translatedCount = episodes.flatMap { it.translatedLanguages }
        .groupingBy { it }.eachCount()

    // framagit.org is IP blocked in some countries
    val titles = try {
        fetchTitles(client, headers)
    } catch (_: Exception) {
        null
    }

    val langs = client.newCall(GET("$baseUrl/0_sources/langs.json", headers))
        .execute().parseAs<LangsDto>().entries.map { (key, dto) ->
            Lang(
                key = key,
                name = dto.localName,
                code = dto.isoCode.ifEmpty { key },
                translators = dto.translators.joinToString(),
                translatedCount = translatedCount[key] ?: 0,
            )
        }
        .filter { it.translatedCount > 0 }
        .groupBy { it.code }.values
        .flatMap { it.sortedByDescending { lang -> lang.translatedCount } }
        .also { if (preferences.lang.isEmpty()) editor.chooseLang(it) }
        .map {
            val progress = "${it.translatedCount}/$total translated"
            LangData(it.key, it.name, progress, it.translators, titles?.get(it.key) ?: if (it.key == "en") TITLE else "$TITLE (${it.key.uppercase()})")
        }

    val encodedLangs = Base64.encodeToString(protoInstance.encodeToByteArray(langs), Base64.NO_WRAP)
    editor.putString(LANG_DATA_PREF, encodedLangs).apply()
}

private fun SharedPreferences.Editor.chooseLang(langs: List<Lang>) {
    val language = Locale.getDefault().language
    val result = langs.filter { it.code == language }.mapTo(ArrayList()) { it.key }
    if (result.isEmpty()) return
    if (language != "en") result.add("en")
    setLang(result)
}

private fun fetchTitles(client: OkHttpClient, headers: Headers): Map<String, String> {
    val url = "https://framagit.org/search?project_id=76196&search=core/mod-header.php:4"
    val document = client.newCall(GET(url, headers)).execute().use { it.asJsoup() }
    val result = hashMapOf<String, String>()
    for (file in document.selectFirst(Evaluator.Class("search-results"))!!.children()) {
        val filename = file.selectFirst(Evaluator.Tag("strong"))!!.ownText()
        if (!filename.endsWith(".po") || !filename.startsWith("po/")) continue
        val lang = filename.substring(3, filename.length - 3)

        val lines = file.select(Evaluator.Class("line"))
        for (i in lines.indices) {
            if (lines[i].ownText() == "msgid \"Pepper&amp;Carrot\"" && i + 1 < lines.size) {
                val title = lines[i + 1].ownText().removePrefix("msgstr \"").removeSuffix("\"")
                val unescaped = unescapeEntities(title, false).trim()
                if (unescaped.isNotEmpty() && unescaped != TITLE) result[lang] = unescaped
                break
            }
        }
    }

    for (sameTitleList in result.entries.groupBy { it.value }.values) {
        if (sameTitleList.size == 1) continue
        for (entry in sameTitleList) {
            entry.setValue("${entry.value} (${entry.key.uppercase()})")
        }
    }

    return result
}

const val TITLE = "Pepper&Carrot"
const val AUTHOR = "David Revoy"

private const val LANG_PREF = "LANG"
private const val LANG_DATA_PREF = "LANG_DATA"
private const val LAST_UPDATED_PREF = "LAST_UPDATED"
private const val HI_RES_PREF = "HI_RES"

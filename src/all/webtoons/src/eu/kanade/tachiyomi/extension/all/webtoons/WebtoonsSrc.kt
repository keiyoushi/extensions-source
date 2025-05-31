package eu.kanade.tachiyomi.extension.all.webtoons

import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.lib.textinterceptor.TextInterceptor
import eu.kanade.tachiyomi.lib.textinterceptor.TextInterceptorHelper
import eu.kanade.tachiyomi.multisrc.webtoons.Webtoons
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Page
import keiyoushi.utils.getPreferencesLazy
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale

open class WebtoonsSrc(
    override val name: String,
    override val baseUrl: String,
    override val lang: String,
    langCode: String = lang,
    override val localeForCookie: String = lang,
    dateFormat: SimpleDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH),
) : ConfigurableSource, Webtoons(name, baseUrl, lang, langCode, localeForCookie, dateFormat) {

    override val client: OkHttpClient = super.client.newBuilder()
        .addInterceptor(TextInterceptor())
        .build()

    private val preferences: SharedPreferences by getPreferencesLazy()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val authorsNotesPref = SwitchPreferenceCompat(screen.context).apply {
            key = SHOW_AUTHORS_NOTES_KEY
            title = "Show author's notes"
            summary = "Enable to see the author's notes at the end of chapters (if they're there)."
            setDefaultValue(false)

            setOnPreferenceChangeListener { _, newValue ->
                val checkValue = newValue as Boolean
                preferences.edit().putBoolean(SHOW_AUTHORS_NOTES_KEY, checkValue).commit()
            }
        }
        screen.addPreference(authorsNotesPref)

        val maxQualityPref = SwitchPreferenceCompat(screen.context).apply {
            key = USE_MAX_QUALITY_KEY
            title = "Use maximum quality images"
            summary = "Enable to load images in maximum quality."
            setDefaultValue(false)

            setOnPreferenceChangeListener { _, newValue ->
                val checkValue = newValue as Boolean
                preferences.edit().putBoolean(USE_MAX_QUALITY_KEY, checkValue).commit()
            }
        }
        screen.addPreference(maxQualityPref)
    }

    private fun showAuthorsNotesPref() = preferences.getBoolean(SHOW_AUTHORS_NOTES_KEY, false)
    private fun useMaxQualityPref() = preferences.getBoolean(USE_MAX_QUALITY_KEY, false)

    override fun pageListParse(document: Document): List<Page> {
        val useMaxQuality = useMaxQualityPref()
        var pages = document.select("div#_imageList > img").mapIndexed { i, element ->
            val imageUrl = element.attr("data-url").toHttpUrl()

            if (useMaxQuality && imageUrl.queryParameter("type") == "q90") {
                val newImageUrl = imageUrl.newBuilder().apply {
                    removeAllQueryParameters("type")
                }.build()
                Page(i, "", newImageUrl.toString())
            } else {
                Page(i, "", imageUrl.toString())
            }
        }

        if (showAuthorsNotesPref()) {
            val note = document.select("div.creator_note p.author_text").text()

            if (note.isNotEmpty()) {
                val creator = document.select("div.creator_note a.author_name span").text().trim()

                pages = pages + Page(
                    pages.size,
                    "",
                    TextInterceptorHelper.createUrl("Author's Notes from $creator", note),
                )
            }
        }

        if (pages.isNotEmpty()) { return pages }

        val docString = document.toString()

        val docUrlRegex = Regex("documentURL:.*?'(.*?)'")
        val motiontoonPathRegex = Regex("jpg:.*?'(.*?)\\{")

        val docUrl = docUrlRegex.find(docString)!!.destructured.toList()[0]
        val motiontoonPath = motiontoonPathRegex.find(docString)!!.destructured.toList()[0]
        val motiontoonResponse = client.newCall(GET(docUrl, headers)).execute()

        val motiontoonJson = json.parseToJsonElement(motiontoonResponse.body.string()).jsonObject
        val motiontoonImages = motiontoonJson["assets"]!!.jsonObject["image"]!!.jsonObject

        return motiontoonImages.entries
            .filter { it.key.contains("layer") }
            .mapIndexed { i, entry ->
                Page(i, "", motiontoonPath + entry.value.jsonPrimitive.content)
            }
    }

    companion object {
        private const val SHOW_AUTHORS_NOTES_KEY = "showAuthorsNotes"
        private const val USE_MAX_QUALITY_KEY = "useMaxQuality"
    }
}

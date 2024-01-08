package eu.kanade.tachiyomi.extension.all.webtoons

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.lib.textinterceptor.TextInterceptor
import eu.kanade.tachiyomi.lib.textinterceptor.TextInterceptorHelper
import eu.kanade.tachiyomi.multisrc.webtoons.Webtoons
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Page
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
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

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

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
    }

    private fun showAuthorsNotesPref() = preferences.getBoolean(SHOW_AUTHORS_NOTES_KEY, false)

    override fun pageListParse(document: Document): List<Page> {
        var pages = document.select("div#_imageList > img").mapIndexed { i, element -> Page(i, "", element.attr("data-url")) }

        if (showAuthorsNotesPref()) {
            val note = document.select("div.creator_note p.author_text").text()

            if (note.isNotEmpty()) {
                val creator = document.select("div.creator_note a.author_name span").text().trim()

                pages = pages + Page(
                    pages.size,
                    "",
                    TextInterceptorHelper.createUrl(creator, note),
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
    }
}

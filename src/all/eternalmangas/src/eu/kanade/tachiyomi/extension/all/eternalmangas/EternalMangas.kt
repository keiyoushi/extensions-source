package eu.kanade.tachiyomi.extension.all.eternalmangas

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.multisrc.mangaesp.MangaEsp
import eu.kanade.tachiyomi.multisrc.mangaesp.SeriesDto
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import okhttp3.FormBody
import okhttp3.Response
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class EternalMangas :
    MangaEsp(
        "EternalMangas",
        "https://eternalmangas.com",
        "all",
    ),
    ConfigurableSource {

    override val id = 1533901034425595323

    private val preferences: SharedPreferences =
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val responseData = json.decodeFromString<LatestUpdatesDto>(response.body.string())
        val language = preferences.getLatestLanguage()
        val mangas = responseData.updates[language]?.flatten()?.map { it.toSManga() } ?: emptyList()
        return MangasPage(mangas, false)
    }

    override fun pageListParse(response: Response): List<Page> {
        var document = response.asJsoup()

        document.selectFirst("body > form[method=post]")?.let {
            val action = it.attr("action")
            val inputs = it.select("input")

            val form = FormBody.Builder()
            inputs.forEach { input ->
                form.add(input.attr("name"), input.attr("value"))
            }

            document = client.newCall(POST(action, headers, form.build())).execute().asJsoup()
        }

        return document.select("main > img").mapIndexed { i, img ->
            Page(i, imageUrl = img.imgAttr())
        }
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = LATEST_LANGUAGE_PREF
            title = LATEST_LANGUAGE_TITLE
            entries = LATEST_LANGUAGE_ENTRIES
            entryValues = LATEST_LANGUAGE_VALUES
            setDefaultValue(LATEST_LANGUAGE_DEFAULT)
        }.also { screen.addPreference(it) }
    }

    private fun SharedPreferences.getLatestLanguage() = getString(LATEST_LANGUAGE_PREF, LATEST_LANGUAGE_DEFAULT)!!

    @Serializable
    class LatestUpdatesDto(
        val updates: Map<String, List<List<SeriesDto>>>,
    )

    companion object {
        const val LATEST_LANGUAGE_PREF = "latest_language_pref"
        const val LATEST_LANGUAGE_TITLE = "Language of latest updates"
        val LATEST_LANGUAGE_ENTRIES = listOf("Spanish", "English", "Portuguese").toTypedArray()
        val LATEST_LANGUAGE_VALUES = listOf("es", "en", "pt").toTypedArray()
        const val LATEST_LANGUAGE_DEFAULT = "es"
    }
}

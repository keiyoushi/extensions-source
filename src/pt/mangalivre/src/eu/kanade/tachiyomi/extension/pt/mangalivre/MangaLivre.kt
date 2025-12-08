package eu.kanade.tachiyomi.extension.pt.mangalivre

import android.app.Application
import android.content.SharedPreferences
import android.util.Base64
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Page
import kotlinx.serialization.decodeFromString
import okhttp3.FormBody
import org.jsoup.nodes.Document
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.SimpleDateFormat
import java.util.Locale

class MangaLivre :
    Madara(
        "Manga Livre",
        "https://mangalivre.tv",
        "pt-BR",
        SimpleDateFormat("dd.MM.yyyy", Locale.ROOT),
    ),
    ConfigurableSource {

    override val id: Long = 2834885536325274328

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    // Define a URL padrão explicitamente para evitar erros de inicialização
    override val baseUrl by lazy {
        preferences.getString(BASE_URL_PREF, "https://mangalivre.tv")!!
    }

    override val client = super.client.newBuilder()
        .rateLimit(2)
        .build()

    override val useNewChapterEndpoint = true
    override val pageListParseSelector = ""

    override fun headersBuilder() = super.headersBuilder()
        .set("Origin", baseUrl)
        .set("Referer", "$baseUrl/")

    override fun chapterListSelector() = "li.wp-manga-chapter, li.chapter-li"

    override fun xhrChaptersRequest(mangaUrl: String) =
        POST("$mangaUrl/ajax/chapters/", xhrHeaders, FormBody.Builder().build())

    override fun pageListParse(document: Document): List<Page> {
        // 1. Tenta o parser padrão primeiro (caso removam a ofuscação)
        val standardPages = runCatching { super.pageListParse(document) }.getOrNull()
        if (!standardPages.isNullOrEmpty()) {
            return standardPages
        }

        // 2. Busca o script (procura por 'atob' ou o hex do 'push')
        val script = document.select("script")
            .asSequence()
            .map { it.data() }
            .firstOrNull { it.contains("atob") || it.contains("\\x70\\x75\\x73\\x68") }
            ?: throw Exception("Script not found. Cloudflare might be blocking the content.")

        // 3. Extrai as partes em Base64
        // Tenta o padrão novo (Array Push)
        var base64String = ARRAY_PUSH_REGEX.findAll(script).joinToString("") { it.groupValues[1] }

        // Fallback: Se o novo padrão falhar, tenta o antigo (+=)
        if (base64String.isEmpty()) {
            base64String = LEGACY_REGEX.findAll(script).joinToString("") { it.groupValues[1] }
        }

        if (base64String.isEmpty()) {
            throw Exception("Failed to extract Base64 data. The obfuscation pattern has changed.")
        }

        return runCatching {
            val decoded = String(Base64.decode(base64String, Base64.DEFAULT))
            json.decodeFromString<List<String>>(decoded)
                .mapIndexed { idx, url -> Page(idx, imageUrl = url.trim()) }
        }.getOrElse {
            throw Exception("Failed to decode images: ${it.message}")
        }
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        EditTextPreference(screen.context).apply {
            key = BASE_URL_PREF
            title = "Override Base URL"
            summary = "Default: https://mangalivre.tv"
            setDefaultValue("https://mangalivre.tv")
            setOnPreferenceChangeListener { _, _ ->
                Toast.makeText(screen.context, "Restart app to apply.", Toast.LENGTH_LONG).show()
                true
            }
        }.also(screen::addPreference)
    }

    companion object {
        private const val BASE_URL_PREF = "overrideBaseUrl"

        // Compila as regex apenas uma vez para melhor performance
        private val ARRAY_PUSH_REGEX = Regex("""\w+\[\w+\]\(['"]([^'"]+)['"]\)""")
        private val LEGACY_REGEX = Regex("""\+=\s*['"]([^'"]*)['"]""")
    }
}

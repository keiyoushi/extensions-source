package eu.kanade.tachiyomi.multisrc.madara

import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.lib.i18n.Intl
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Locked-chapter bypass for Madara sites that ship the `icmadara` (a.k.a.
 * "Infocomic Madara") WordPress plugin. The plugin exposes
 * `/wp-json/icmadara/v1/mangas/slug/<slug>` (full chapter list with post
 * ids and slugs) and `/wp-json/icmadara/v1/capitulo/<id>` (chapter image
 * list). Both are unauthenticated, so the standard `wp-manga-chapter-coin`
 * / `mycred` lock plugins — which only filter the public HTML — never
 * see them.
 *
 * Wire-up in an extension (see `ResetScans` and `Utoon`):
 *
 *  1. Override `chapterListParse`. When the bypass is enabled, call
 *     [fetchChaptersFromIcmadara] and use that as the source of truth —
 *     it includes locked chapters with proper URLs and stamps the
 *     capitulo id into each chapter's URL fragment. Falls back to the
 *     parent's HTML-based parse on any failure.
 *  2. Override `pageListRequest`. If the URL carries an encoded capitulo
 *     id and the bypass is enabled, short-circuit to [restRequest].
 *  3. Override `pageListParse(response)`. JSON responses go through
 *     [parseRestResponse]; HTML responses fall through to the default
 *     Madara parser.
 */
class MadaraIcmadaraPageFallback(
    private val prefKey: String = "pref_unlock_paid_chapters",
    private val defaultEnabled: Boolean = false,
    /** Path segment between the base URL and the slug (defaults to `manga`). */
    private val mangaSubString: String = "manga",
) {

    fun addPreferenceToScreen(screen: PreferenceScreen, intl: Intl) {
        SwitchPreferenceCompat(screen.context).apply {
            key = prefKey
            title = intl["pref_unlock_paid_chapters_title"]
            summary = intl["pref_unlock_paid_chapters_summary"]
            setDefaultValue(defaultEnabled)
        }.also(screen::addPreference)
    }

    fun isEnabled(prefs: SharedPreferences): Boolean = prefs.getBoolean(prefKey, defaultEnabled)

    fun decodeCapituloId(chapterUrl: String): Int? = CAPITULO_FRAGMENT_REGEX.find(chapterUrl)?.groupValues?.get(1)?.toIntOrNull()

    fun restRequest(baseUrl: String, capituloId: Int, headers: Headers): Request = GET("$baseUrl/wp-json/icmadara/v1/capitulo/$capituloId", headers)

    /** Parse pages from a successful icmadara `capitulo` response. */
    fun parseRestResponse(response: Response, chapterUrl: String): List<Page> {
        val payload = try {
            response.parseAs<CapituloResponseDto>()
        } catch (_: Exception) {
            return emptyList()
        }
        if (!payload.request) return emptyList()
        return payload.imagenes
            .mapNotNull { it.src.takeIf(String::isNotBlank) }
            .mapIndexed { i, url -> Page(i, url = chapterUrl, imageUrl = url) }
    }

    /**
     * Returns the full chapter list (free + locked) from the icmadara
     * mangas-by-slug endpoint. Each chapter URL is built from the manga
     * slug + chapter slug and carries the capitulo id as a fragment so
     * `pageListRequest` can short-circuit straight to the REST endpoint.
     * Returns an empty list on any failure — callers should fall back to
     * the parent's HTML parser in that case.
     */
    fun fetchChaptersFromIcmadara(
        baseUrl: String,
        mangaSlug: String,
        client: OkHttpClient,
        headers: Headers,
    ): List<SChapter> {
        if (mangaSlug.isBlank()) return emptyList()
        val url = "$baseUrl/wp-json/icmadara/v1/mangas/slug/$mangaSlug"
        val payload = try {
            client.newCall(GET(url, headers)).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                response.parseAs<MangasResponseDto>()
            }
        } catch (_: Exception) {
            return emptyList()
        }
        val manga = payload.mangas.firstOrNull() ?: return emptyList()
        val mangaPath = "/$mangaSubString/$mangaSlug"
        return manga.capitulos
            .filter { it.idCapitulo > 0 && it.slug.isNotBlank() }
            .sortedByDescending { it.numero() }
            .map { it.toSChapter(mangaPath) }
    }

    @Serializable
    private class MangasResponseDto(
        val request: Boolean = false,
        val mangas: List<MangaDto> = emptyList(),
    )

    @Serializable
    private class MangaDto(val capitulos: List<CapituloMetaDto> = emptyList())

    @Serializable
    private class CapituloMetaDto(
        @SerialName("id_capitulo") val idCapitulo: Int = 0,
        val slug: String = "",
        val nombre: String = "",
        @SerialName("nombre_extendido") val nombreExtendido: String = "",
        @SerialName("fecha_publicacion") val fechaPublicacion: String = "",
        val monedas: Int = 0,
    ) {
        fun numero(): Float {
            // Try to parse a number out of the slug or the title; falls back
            // to 0 so unknown chapters land at the end of the descending sort.
            val candidate = NUMBER_REGEX.find(slug)?.value
                ?: NUMBER_REGEX.find(nombre)?.value
                ?: return 0f
            return candidate.toFloatOrNull() ?: 0f
        }

        fun toSChapter(mangaPath: String): SChapter = SChapter.create().apply {
            // chapter.url is stored verbatim by Mihon — encode the capitulo id
            // in a URL fragment so pageListRequest can find it later.
            url = "$mangaPath/$slug/#$CAPITULO_FRAGMENT_KEY=$idCapitulo"
            name = buildString {
                append(nombre.ifBlank { slug.replaceFirstChar { it.uppercase() } })
                if (nombreExtendido.isNotBlank()) {
                    append(" — ")
                    append(nombreExtendido)
                }
            }
            date_upload = ICMADARA_DATE_FORMAT.tryParse(fechaPublicacion)
        }

        companion object {
            private val NUMBER_REGEX = Regex("""\d+(?:[.,]\d+)?""")
            private val ICMADARA_DATE_FORMAT = SimpleDateFormat("dd-MM-yyyy", Locale.ENGLISH)
        }
    }

    @Serializable
    private class CapituloResponseDto(
        val request: Boolean = false,
        val imagenes: List<ImagenDto> = emptyList(),
    )

    @Serializable
    private class ImagenDto(val src: String = "")

    companion object {
        private const val CAPITULO_FRAGMENT_KEY = "icmadara_capitulo"
        private val CAPITULO_FRAGMENT_REGEX = Regex("""#${CAPITULO_FRAGMENT_KEY}=(\d+)""")
    }
}

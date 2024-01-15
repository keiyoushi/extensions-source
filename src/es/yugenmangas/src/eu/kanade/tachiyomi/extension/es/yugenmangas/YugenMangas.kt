package eu.kanade.tachiyomi.extension.es.yugenmangas

import android.app.Application
import android.content.SharedPreferences
import eu.kanade.tachiyomi.multisrc.heancms.Genre
import eu.kanade.tachiyomi.multisrc.heancms.HeanCms
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.ProtocolException
import okhttp3.Response
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class YugenMangas :
    HeanCms(
        "YugenMangas",
        "https://yugenmangas.net",
        "es",
        "https://api.yugenmangas.net",
    ) {

    // Site changed from Madara to HeanCms.
    override val versionId = 2

    override val baseUrl by lazy { getPrefBaseUrl() }

    private val preferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    private var lastDomain = ""

    private fun domainChangeIntercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        if (!request.url.host.startsWith("yugenmangas")) {
            return chain.proceed(request)
        }

        if (lastDomain.isNotEmpty()) {
            val newUrl = request.url.newBuilder()
                .host(preferences.baseUrlHost)
                .build()

            return chain.proceed(
                request.newBuilder()
                    .url(newUrl)
                    .build(),
            )
        }

        val response = try {
            chain.proceed(request)
        } catch (e: ProtocolException) {
            if (e.message?.contains("Too many follow-up requests") == true) {
                throw IOException("No se pudo obtener la nueva URL del sitio")
            } else {
                throw e
            }
        }

        if (request.url.host == response.request.url.host) return response

        response.close()

        preferences.baseUrlHost = response.request.url.host

        lastDomain = request.url.host

        val newUrl = request.url.newBuilder()
            .host(response.request.url.host)
            .build()

        return chain.proceed(
            request.newBuilder()
                .url(newUrl)
                .build(),
        )
    }

    override val slugStrategy = SlugStrategy.ID
    override val useNewQueryEndpoint = true

    override val client = super.client.newBuilder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .rateLimitHost(apiUrl.toHttpUrl(), 2, 3)
        .addInterceptor(::domainChangeIntercept)
        .build()

    override val coverPath: String = ""

    override val dateFormat: SimpleDateFormat = super.dateFormat.apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    override fun getGenreList(): List<Genre> = listOf(
        Genre("+18", 1),
        Genre("Acción", 36),
        Genre("Adulto", 38),
        Genre("Apocalíptico", 3),
        Genre("Artes marciales (1)", 16),
        Genre("Artes marciales (2)", 37),
        Genre("Aventura", 2),
        Genre("Boys Love", 4),
        Genre("Ciencia ficción", 39),
        Genre("Comedia", 5),
        Genre("Demonios", 6),
        Genre("Deporte", 26),
        Genre("Drama", 7),
        Genre("Ecchi", 8),
        Genre("Familia", 9),
        Genre("Fantasía", 10),
        Genre("Girls Love", 11),
        Genre("Gore", 12),
        Genre("Harem", 13),
        Genre("Harem inverso", 14),
        Genre("Histórico", 48),
        Genre("Horror", 41),
        Genre("Isekai", 40),
        Genre("Josei", 15),
        Genre("Maduro", 42),
        Genre("Magia", 17),
        Genre("MangoScan", 35),
        Genre("Mecha", 18),
        Genre("Militar", 19),
        Genre("Misterio", 20),
        Genre("Psicológico", 21),
        Genre("Realidad virtual", 46),
        Genre("Recuentos de la vida", 25),
        Genre("Reencarnación", 22),
        Genre("Regresion", 23),
        Genre("Romance", 24),
        Genre("Seinen", 27),
        Genre("Shonen", 28),
        Genre("Shoujo", 29),
        Genre("Sistema", 45),
        Genre("Smut", 30),
        Genre("Supernatural", 31),
        Genre("Supervivencia", 32),
        Genre("Tragedia", 33),
        Genre("Transmigración", 34),
        Genre("Vida Escolar", 47),
        Genre("Yaoi", 43),
        Genre("Yuri", 44),
    )

    companion object {
        private const val defaultBaseUrlHost = "yugenmangas.net"
        private const val BASE_URL_PREF = "prefOverrideBaseUrl"
    }

    private var SharedPreferences.baseUrlHost
        get() = getString(BASE_URL_PREF, defaultBaseUrlHost) ?: defaultBaseUrlHost
        set(newHost) {
            edit().putString(BASE_URL_PREF, newHost).commit()
        }

    private fun getPrefBaseUrl(): String = preferences.baseUrlHost.let { "https://$it" }
}

package eu.kanade.tachiyomi.extension.tr.ruyamanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import okhttp3.Headers
import okhttp3.OkHttpClient
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class RuyaManga : Madara() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ENGLISH)
    override val filterNonMangaItems = false

    // Covers go through Jetpack Photon (i*.wp.com) and the origin bucket is private, so only the
    // variants Photon already cached can be served: it answers 403 unless the request accepts webp,
    // and the listing (w=175) and details (w=193) sizes are cached independently, so on 403 try
    // the other one. The cache is keyed on the exact query string, hence the literal query.
    override fun Headers.Builder.configureHeaders(): Headers.Builder = add("Accept", "image/webp,*/*")

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addInterceptor { chain ->
        val request = chain.request()
        val response = chain.proceed(request)
        if (response.code != 403 || !request.url.host.endsWith(".wp.com")) return@addInterceptor response
        val size = request.url.queryParameter("w") ?: return@addInterceptor response
        val (w, h) = if (size == "175") "193" to "278" else "175" to "238"
        response.close()
        val url = request.url.newBuilder().encodedQuery("w=$w&resize=$w,$h&ssl=1").build()
        chain.proceed(request.newBuilder().url(url).build())
    }
}

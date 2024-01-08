package eu.kanade.tachiyomi.extension.id.neumanga

import eu.kanade.tachiyomi.multisrc.zmanga.ZManga
import okhttp3.Headers
import java.text.SimpleDateFormat
import java.util.Locale

class Neumanga : ZManga("Neumanga", "https://neumanga.net", "id", SimpleDateFormat("MMMM dd, yyyy", Locale("id"))) {

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9")
}

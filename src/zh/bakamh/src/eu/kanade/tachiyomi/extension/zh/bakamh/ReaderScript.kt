package eu.kanade.tachiyomi.extension.zh.bakamh

import app.cash.quickjs.QuickJs
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonString

internal const val READER_CONFIG_SELECTOR = "script#mkjp-reader-cfg"
internal const val READER_SLOT_SELECTOR = ".mkjp-slot"

/** The site's reader script, which holds the rotating table that decodes the reader config's page list. */
internal class ReaderScript(val url: String, private val script: String) {

    fun pageUrls(config: String, slots: Int): List<String> {
        val urls = QuickJs.create().use { js ->
            val stub = javaClass.getResource("/assets/reader_stub.js")!!.readText()
            js.evaluate("($stub)(${config.toJsonString()}, $slots)")
            // The script stops with an error at its layout code, after it has filled every slot
            runCatching { js.evaluate(script) }
            js.evaluate("JSON.stringify(mkjpUrls)") as String
        }.parseAs<List<String>>()
        check(urls.isNotEmpty() && urls.all(String::isNotEmpty)) { "Failed to read the page list" }
        return urls
    }
}

package eu.kanade.tachiyomi.extension.vi.lavibitwindroom

import android.content.SharedPreferences
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

private fun OkHttpClient.getString(url: String): String {
    val response = newCall(Request.Builder().url(url).build()).execute()
    return response.use { it.body.string() }
}

object ApiConfigCache {
    private const val KEY_URL = "supabase_api_url"
    private const val KEY_TOKEN = "supabase_api_key"

    fun load(prefs: SharedPreferences): Pair<String, String>? {
        val url = prefs.getString(KEY_URL, null) ?: return null
        val key = prefs.getString(KEY_TOKEN, null) ?: return null
        return url to key
    }

    fun save(prefs: SharedPreferences, url: String, key: String) {
        prefs.edit()
            .putString(KEY_URL, url)
            .putString(KEY_TOKEN, key)
            .apply()
    }

    fun invalidate(prefs: SharedPreferences) {
        prefs.edit()
            .remove(KEY_URL)
            .remove(KEY_TOKEN)
            .apply()
    }
}

object ApiConfigScraper {
    class Config(
        val apiUrl: String,
        val apiKey: String,
    )

    suspend fun scrape(client: OkHttpClient, baseUrl: String, prefs: SharedPreferences): Config {
        ApiConfigCache.load(prefs)?.let { (url, key) ->
            return Config("$url/rest/v1", key)
        }

        val config = scrapeFromSite(client, baseUrl)
        ApiConfigCache.save(prefs, config.apiUrl.removeSuffix("/rest/v1"), config.apiKey)
        return config
    }

    suspend fun rescrape(client: OkHttpClient, baseUrl: String, prefs: SharedPreferences): Config {
        ApiConfigCache.invalidate(prefs)
        return scrape(client, baseUrl, prefs)
    }

    private suspend fun scrapeFromSite(client: OkHttpClient, baseUrl: String): Config {
        val home = client.getString(baseUrl)

        // Key may be inlined in the page itself on some deployments.
        findConfig(home)?.let { return it }

        val scripts = SCRIPT_SRC_RE.findAll(home)
            .map { it.groupValues[1] }
            .map { ref -> if (ref.startsWith("http")) ref else baseUrl.trimEnd('/') + ref }
            .toList()

        for (src in scripts) {
            val body = runCatching { client.getString(src) }.getOrDefault("")
            findConfig(body)?.let { return it }
        }

        throw IOException("Supabase API config not found")
    }

    private fun findConfig(body: String): Config? {
        val host = SUPABASE_URL_RE.find(body)?.groupValues?.get(1) ?: return null
        val key = PUBLISHABLE_KEY_RE.find(body)?.value
            ?: ANON_JWT_RE.find(body)?.value
            ?: return null
        return Config("https://$host.supabase.co/rest/v1", key)
    }

    private val SCRIPT_SRC_RE = Regex("""<script[^>]+src="(/_next/[^"]+\.js[^"]*)"""")
    private val SUPABASE_URL_RE = Regex("""https://([a-z0-9]+)\.supabase\.co""")
    private val PUBLISHABLE_KEY_RE = Regex("""sb_publishable_[A-Za-z0-9_-]+""")
    private val ANON_JWT_RE = Regex("""eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{20,}\.[A-Za-z0-9_-]{10,}""")
}

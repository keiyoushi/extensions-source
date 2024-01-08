package eu.kanade.tachiyomi.extension.ko.newtoki

import android.util.Log
import eu.kanade.tachiyomi.network.GET
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

var domainNumber = ""
    get() {
        val currentValue = field
        if (currentValue.isNotEmpty()) return currentValue

        val prefValue = newTokiPreferences.domainNumber
        if (prefValue.isNotEmpty()) {
            field = prefValue
            return prefValue
        }

        val fallback = fallbackDomainNumber
        domainNumber = fallback
        return fallback
    }
    set(value) {
        for (preference in arrayOf(manaTokiPreferences, newTokiPreferences)) {
            preference.domainNumber = value
        }
        field = value
    }

object DomainInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        val response = try {
            chain.proceed(request)
        } catch (e: IOException) {
            if (chain.call().isCanceled()) throw e
            Log.e("NewToki", "failed to fetch ${request.url}", e)

            val newDomainNumber = try {
                val domainNumberUrl = "https://stevenyomi.github.io/source-domains/newtoki.txt"
                chain.proceed(GET(domainNumberUrl)).body.string().also { it.toInt() }
            } catch (_: Throwable) {
                throw IOException(editDomainNumber(), e)
            }
            domainNumber = newDomainNumber

            val url = request.url
            val newHost = numberRegex.replaceFirst(url.host, newDomainNumber)
            val newUrl = url.newBuilder().host(newHost).build()
            try {
                chain.proceed(request.newBuilder().url(newUrl).build())
            } catch (e: IOException) {
                Log.e("NewToki", "failed to fetch $newUrl", e)
                throw IOException(editDomainNumber(), e)
            }
        }

        if (response.priorResponse == null) return response

        val newUrl = response.request.url
        if ("captcha" in newUrl.toString()) throw IOException(solveCaptcha())

        val newHost = newUrl.host
        if (newHost.startsWith(MANATOKI_PREFIX) || newHost.startsWith(NEWTOKI_PREFIX)) {
            numberRegex.find(newHost)?.run { domainNumber = value }
        }
        return response
    }

    private val numberRegex by lazy { Regex("""\d+|$fallbackDomainNumber""") }
}

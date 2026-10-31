package keiyoushi.lib.cronet

import android.content.Context
import android.util.Log
import eu.kanade.tachiyomi.network.NetworkHelper
import keiyoushi.utils.applicationContext
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.internal.connection.RealCall
import uy.kohesive.injekt.injectLazy
import java.io.IOException
import java.lang.reflect.Constructor
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * Sends the client's requests through the device's Cronet (the platform's HttpEngine, else Play services'),
 * for Chrome's TLS and HTTP/2. Cronet resolves hosts itself, so the client's DNS (DoH included) isn't used;
 * the first requests after app start wait up to 5 s for Play services' Cronet. Requests stay on OkHttp
 * without Cronet, and for clients with their own TLS, proxy or authenticator.
 */
fun OkHttpClient.Builder.cronet(): OkHttpClient.Builder = apply {
    if (System.getProperty("java.vm.name") == "Dalvik") {
        addInterceptor(CronetInterceptor)
    } else {
        logOnce("Not on Android, staying on OkHttp")
    }
}

/** Runs the rest of OkHttp's chain itself, with Cronet in place of OkHttp's network call */
private object CronetInterceptor : Interceptor {
    private val network: NetworkHelper by injectLazy()

    private val bridge by lazy { okHttpInterceptor("okhttp3.internal.http.BridgeInterceptor") }
    private val cache by lazy { okHttpInterceptor("okhttp3.internal.cache.CacheInterceptor") }

    override fun intercept(chain: Interceptor.Chain): Response {
        if (chain is CronetChain) return chain.proceed(chain.request())
        val server = CronetRuntime.server() ?: run {
            logOnce("No Cronet yet, staying on OkHttp")
            return chain.proceed(chain.request())
        }
        val client = (chain.call() as? RealCall)?.client ?: run {
            logOnce("Call isn't OkHttp's own, staying on OkHttp")
            return chain.proceed(chain.request())
        }

        // A cleartext-only client has no TLS settings to read
        val setting = runCatching { unsupportedSetting(client) }.getOrElse { "connection specs" }
        if (setting != null) {
            logOnce("Client sets its own $setting, staying on OkHttp")
            return chain.proceed(chain.request())
        }

        val interceptors = runCatching {
            buildList {
                addAll(client.interceptors.let { it.subList(it.indexOf(this@CronetInterceptor) + 1, it.size) })
                add(FollowUpInterceptor(client))
                add(bridge.getOrThrow()(client.cookieJar))
                add(cache.getOrThrow()(client.cache))
                addAll(client.networkInterceptors)
                add(Interceptor { chain -> server.interceptAsIo(chain) })
            }
        }.getOrElse {
            logOnce("OkHttp's interceptors not found, staying on OkHttp", it)
            return chain.proceed(chain.request())
        }
        return CronetChain(chain, interceptors, 0, chain.request()).proceed(chain.request())
    }

    /** A setting Cronet can't take; DNS is Cronet's own, DoH included */
    private fun unsupportedSetting(client: OkHttpClient): String? {
        val app = network.client
        return when {
            client.sslSocketFactory !== app.sslSocketFactory -> "SSL socket factory"
            client.hostnameVerifier !== app.hostnameVerifier -> "hostname verifier"
            client.certificatePinner != app.certificatePinner -> "certificate pinner"
            client.proxy != app.proxy || client.proxySelector !== app.proxySelector -> "proxy"
            client.authenticator !== Authenticator.NONE -> "authenticator"
            else -> null
        }
    }

    /** OkHttp 5.4 reads the cookie jar and cache from the chain, earlier versions take them as arguments */
    private fun okHttpInterceptor(name: String): Result<(Any?) -> Interceptor> = runCatching {
        val type = Class.forName(name)
        val withArg: Constructor<*>? = type.constructors.firstOrNull { it.parameterTypes.size == 1 }
        val noArg = if (withArg == null) type.getConstructor() else null
        return@runCatching { arg -> (withArg?.newInstance(arg) ?: noArg!!.newInstance()) as Interceptor }
    }
}

// A runtime that doesn't link or resolve fails the request, not the app
private fun Interceptor.interceptAsIo(chain: Interceptor.Chain): Response = try {
    intercept(chain)
} catch (e: LinkageError) {
    throw IOException(e)
} catch (e: ReflectiveOperationException) {
    throw IOException(e)
}

private const val TAG = "KeiyoushiCronet"

private val logged = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

private fun logOnce(message: String, error: Throwable? = null) {
    if (logged.add(message)) Log.i(TAG, message, error)
}

private object CronetRuntime {
    private val server by lazy {
        runCatching { RuntimeLoader.load().getMethod("server", Context::class.java) }
            .onFailure { Log.e(TAG, "Runtime not loaded, staying on OkHttp", it) }
    }

    fun server(): Interceptor? = server
        .mapCatching { it.invoke(null, applicationContext) as Interceptor? }
        .onFailure { logOnce("Runtime failed, staying on OkHttp", it) }
        .getOrNull()
}

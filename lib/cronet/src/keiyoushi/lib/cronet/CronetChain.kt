package keiyoushi.lib.cronet

import okhttp3.Authenticator
import okhttp3.Cache
import okhttp3.CertificatePinner
import okhttp3.ConnectionPool
import okhttp3.CookieJar
import okhttp3.Dns
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.net.Proxy
import java.net.ProxySelector
import java.util.concurrent.TimeUnit
import javax.net.SocketFactory
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager

/** OkHttp's chain over [interceptors], with the call and its settings from [base] */
internal class CronetChain(
    private val base: Interceptor.Chain,
    private val interceptors: List<Interceptor>,
    private val index: Int,
    private val request: Request,
) : Interceptor.Chain by base {
    override fun request(): Request = request

    override fun proceed(request: Request): Response = interceptors[index].intercept(CronetChain(base, interceptors, index + 1, request))

    private fun with(base: Interceptor.Chain) = CronetChain(base, interceptors, index, request)

    override fun withConnectTimeout(timeout: Int, unit: TimeUnit) = with(base.withConnectTimeout(timeout, unit))
    override fun withReadTimeout(timeout: Int, unit: TimeUnit) = with(base.withReadTimeout(timeout, unit))
    override fun withWriteTimeout(timeout: Int, unit: TimeUnit) = with(base.withWriteTimeout(timeout, unit))
    override fun withDns(dns: Dns) = with(base.withDns(dns))
    override fun withSocketFactory(socketFactory: SocketFactory) = with(base.withSocketFactory(socketFactory))
    override fun withRetryOnConnectionFailure(retryOnConnectionFailure: Boolean) = with(base.withRetryOnConnectionFailure(retryOnConnectionFailure))
    override fun withAuthenticator(authenticator: Authenticator) = with(base.withAuthenticator(authenticator))
    override fun withCookieJar(cookieJar: CookieJar) = with(base.withCookieJar(cookieJar))
    override fun withCache(cache: Cache?) = with(base.withCache(cache))
    override fun withProxy(proxy: Proxy?) = with(base.withProxy(proxy))
    override fun withProxySelector(proxySelector: ProxySelector) = with(base.withProxySelector(proxySelector))
    override fun withProxyAuthenticator(proxyAuthenticator: Authenticator) = with(base.withProxyAuthenticator(proxyAuthenticator))
    override fun withSslSocketFactory(sslSocketFactory: SSLSocketFactory?, x509TrustManager: X509TrustManager?) = with(base.withSslSocketFactory(sslSocketFactory, x509TrustManager))
    override fun withHostnameVerifier(hostnameVerifier: HostnameVerifier) = with(base.withHostnameVerifier(hostnameVerifier))
    override fun withCertificatePinner(certificatePinner: CertificatePinner) = with(base.withCertificatePinner(certificatePinner))
    override fun withConnectionPool(connectionPool: ConnectionPool) = with(base.withConnectionPool(connectionPool))
}

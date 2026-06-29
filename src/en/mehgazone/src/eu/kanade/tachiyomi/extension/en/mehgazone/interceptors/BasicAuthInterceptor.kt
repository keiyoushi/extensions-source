package eu.kanade.tachiyomi.extension.en.mehgazone.interceptors

import okhttp3.Credentials
import okhttp3.Interceptor
import okhttp3.Response

class BasicAuthInterceptor(private var user: String?, private var password: String?) : Interceptor {
    fun setUser(user: String?) {
        setAuth(user, password)
    }

    fun setPassword(password: String?) {
        setAuth(user, password)
    }

    fun setAuth(user: String?, password: String?) {
        this.user = user
        this.password = password
        credentials = getCredentials()
    }

    private fun getCredentials(): String? = if (!user.isNullOrBlank() && !password.isNullOrBlank()) {
        Credentials.basic(user!!, password!!)
    } else {
        null
    }

    private var credentials: String? = getCredentials()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        if (
            !request.url.encodedPath.contains("/wp-json/wp/v2/") ||
            user.isNullOrBlank() ||
            password.isNullOrBlank() ||
            credentials.isNullOrBlank()
        ) {
            return chain.proceed(request)
        }

        val authenticatedRequest = request.newBuilder()
            .header("Authorization", credentials!!)
            .build()

        return chain.proceed(authenticatedRequest)
    }
}

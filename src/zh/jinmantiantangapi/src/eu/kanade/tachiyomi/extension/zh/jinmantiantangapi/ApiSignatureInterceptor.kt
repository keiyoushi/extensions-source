package eu.kanade.tachiyomi.extension.zh.jinmantiantangapi

import okhttp3.Interceptor
import okhttp3.Response

/**
 * API 请求签名拦截器
 *
 * 为每个 API 请求自动添加 token 和 tokenparam 请求头
 * 这是移动端 API 的必需认证机制
 */
class ApiSignatureInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val originalUrl = originalRequest.url

        // 生成时间戳（秒）
        val timestamp = System.currentTimeMillis() / 1000

        // 判断是否需要特殊签名（如 chapter_view_template）
        val useSpecialSecret = originalUrl.toString().contains("/chapter_view_template")

        // 生成 token 和 tokenparam
        // 关键：GET 请求的 tokenparam 需要使用空版本号，形成 "timestamp,"
        // POST 请求才携带 APP_VERSION（如登录等）
        val useEmptyVersion = originalRequest.method.equals("GET", ignoreCase = true)
        val (token, tokenparam) = if (useSpecialSecret) {
            if (useEmptyVersion) {
                JmCryptoTool.generateSpecialToken(timestamp).let { (specialToken, _) ->
                    specialToken to "$timestamp,"
                }
            } else {
                JmCryptoTool.generateSpecialToken(timestamp)
            }
        } else {
            if (useEmptyVersion) {
                JmCryptoTool.generateToken(timestamp, version = "")
            } else {
                JmCryptoTool.generateToken(timestamp)
            }
        }

        // 构建新请求
        val newRequest = originalRequest.newBuilder()
            .header("token", token)
            .header("tokenparam", tokenparam)
            .header("X-Request-Timestamp", timestamp.toString())
            .header("User-Agent", JmConstants.USER_AGENT)
            .header("Accept-Encoding", "gzip, deflate")
            .header("X-Requested-With", JmConstants.IMAGE_X_REQUESTED_WITH) // 必需：标识来自官方应用
            .build()

        // 执行请求
        val response = chain.proceed(newRequest)

        // 将时间戳附加到响应中，供解密拦截器使用
        return response.newBuilder()
            .addHeader("X-Request-Timestamp", timestamp.toString())
            .build()
    }
}

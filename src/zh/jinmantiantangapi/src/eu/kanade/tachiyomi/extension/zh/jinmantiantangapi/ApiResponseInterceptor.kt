package eu.kanade.tachiyomi.extension.zh.jinmantiantangapi

import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject

/**
 * API 响应解密拦截器
 *
 * 自动解密 API 返回的加密数据
 * API 响应格式：{"code": 200, "data": "加密的Base64字符串", ...}
 * 解密后替换 data 字段为明文 JSON
 */
class ApiResponseInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        // 只处理成功的 JSON 响应
        if (!response.isSuccessful) {
            return response
        }

        val contentType = response.body?.contentType()
        if (contentType?.toString()?.contains("json") != true) {
            return response
        }

        // 读取响应体
        val responseBody = response.body?.string() ?: return response

        try {
            // 解析 JSON
            val json = JSONObject(responseBody)

            // 检查是否有加密的 data 字段
            if (!json.has("data") || json.isNull("data")) {
                // 没有 data 字段，直接返回原响应
                return response.newBuilder()
                    .body(responseBody.toResponseBody(contentType))
                    .build()
            }

            val dataField = json.get("data")

            // 如果 data 不是字符串，说明已经是明文，直接返回
            if (dataField !is String) {
                return response.newBuilder()
                    .body(responseBody.toResponseBody(contentType))
                    .build()
            }

            // 获取请求时的时间戳
            val timestamp = request.header("X-Request-Timestamp")?.toLongOrNull()
                ?: response.request.header("X-Request-Timestamp")?.toLongOrNull()
                ?: response.header("X-Request-Timestamp")?.toLongOrNull()
            if (timestamp == null) {
                // 没有时间戳，无法解密，返回原响应
                return response.newBuilder()
                    .body(responseBody.toResponseBody(contentType))
                    .build()
            }

            // 解密 data 字段
            val decryptedData = try {
                JmCryptoTool.decryptResponse(dataField, timestamp)
            } catch (e: Exception) {
                // 解密失败，可能 data 本身就是明文
                // 尝试解析为 JSON，如果成功则是明文
                try {
                    JSONObject(dataField)
                    dataField // 是明文 JSON
                } catch (e2: Exception) {
                    // 既不是加密数据也不是明文 JSON，返回原响应
                    return response.newBuilder()
                        .body(responseBody.toResponseBody(contentType))
                        .build()
                }
            }

            // 替换 data 字段为解密后的数据
            json.put("data", JSONObject(decryptedData))

            // 构建新的响应
            val newResponseBody = json.toString()
            return response.newBuilder()
                .body(newResponseBody.toResponseBody(contentType))
                .build()
        } catch (e: Exception) {
            // JSON 解析失败，返回原响应
            return response.newBuilder()
                .body(responseBody.toResponseBody(contentType))
                .build()
        }
    }
}

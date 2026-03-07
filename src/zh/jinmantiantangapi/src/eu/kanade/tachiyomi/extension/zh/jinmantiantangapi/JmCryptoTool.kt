package eu.kanade.tachiyomi.extension.zh.jinmantiantangapi

import android.util.Base64
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * 禁漫天堂加密工具类
 * 实现 Token 签名、AES 解密等核心加密功能
 */
object JmCryptoTool {
    data class DomainOption(
        val domain: String,
        val label: String? = null,
    )

    /**
     * 生成 API 请求所需的 token 和 tokenparam
     *
     * @param timestamp 时间戳（秒）
     * @param version APP 版本号，默认使用常量配置
     * @param secret Token 签名密钥，默认使用标准密钥
     * @return Pair<token, tokenparam>
     *
     * 示例：
     * - timestamp = 1700566805
     * - version = "2.0.18"
     * - secret = "18comicAPP"
     * - token = MD5("1700566805" + "18comicAPP") = "81498a20feea7fbb7149c637e49702e3"
     * - tokenparam = "1700566805,2.0.18"
     */
    fun generateToken(
        timestamp: Long,
        version: String = JmConstants.APP_VERSION,
        secret: String = JmConstants.APP_TOKEN_SECRET,
    ): Pair<String, String> {
        val token = md5("$timestamp$secret")
        val tokenparam = "$timestamp,$version"
        return Pair(token, tokenparam)
    }

    /**
     * 生成特殊接口的 token（如 chapter_view_template）
     * 使用不同的密钥
     */
    fun generateSpecialToken(timestamp: Long): Pair<String, String> = generateToken(
        timestamp = timestamp,
        secret = JmConstants.APP_TOKEN_SECRET_2,
    )

    /**
     * MD5 哈希函数
     *
     * @param input 输入字符串
     * @return 32位小写十六进制 MD5 值
     */
    fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * 解密 API 响应数据
     *
     * API 返回的 data 字段是加密的，解密流程：
     * 1. Base64 解码
     * 2. AES-ECB 解密（key = MD5(timestamp + secret)）
     * 3. 移除 PKCS5 padding
     * 4. UTF-8 解码为 JSON 字符串
     *
     * @param encryptedData 加密的 Base64 字符串（来自响应的 data 字段）
     * @param timestamp 请求时使用的时间戳（秒）
     * @param secret 数据解密密钥，默认使用标准密钥
     * @return 解密后的 JSON 字符串
     *
     * @throws Exception 解密失败时抛出异常
     */
    fun decryptResponse(
        encryptedData: String,
        timestamp: Long,
        secret: String = JmConstants.APP_DATA_SECRET,
    ): String {
        try {
            // 1. 生成 AES 密钥：MD5(timestamp + secret)
            val keyString = md5("$timestamp$secret")
            val keyBytes = keyString.toByteArray(Charsets.UTF_8)
            val secretKey = SecretKeySpec(keyBytes, "AES")

            // 2. Base64 解码
            val encryptedBytes = Base64.decode(encryptedData, Base64.DEFAULT)

            // 3. AES-ECB 解密
            val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey)
            val decryptedBytes = cipher.doFinal(encryptedBytes)

            // 4. UTF-8 解码
            return String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            throw Exception("解密响应数据失败: ${e.message}", e)
        }
    }

    /**
     * 加密请求数据（如果需要）
     *
     * 某些 POST 请求可能需要加密请求体
     * 加密流程是解密的逆过程：
     * 1. UTF-8 编码
     * 2. AES-ECB 加密
     * 3. Base64 编码
     *
     * @param plainData 明文数据
     * @param timestamp 时间戳（秒）
     * @param secret 数据加密密钥
     * @return 加密后的 Base64 字符串
     */
    fun encryptRequest(
        plainData: String,
        timestamp: Long,
        secret: String = JmConstants.APP_DATA_SECRET,
    ): String {
        try {
            // 1. 生成 AES 密钥
            val keyString = md5("$timestamp$secret")
            val keyBytes = keyString.toByteArray(Charsets.UTF_8)
            val secretKey = SecretKeySpec(keyBytes, "AES")

            // 2. UTF-8 编码
            val plainBytes = plainData.toByteArray(Charsets.UTF_8)

            // 3. AES-ECB 加密
            val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val encryptedBytes = cipher.doFinal(plainBytes)

            // 4. Base64 编码（去除换行符）
            return Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
            return Base64.encodeToString(encryptedBytes, Base64.DEFAULT)
        } catch (e: Exception) {
            throw Exception("加密请求数据失败: ${e.message}", e)
        }
    }

    /**
     * 计算图片混淆的分段数
     *
     * 禁漫天堂对图片进行了混淆处理，需要根据漫画 ID 和图片索引计算分段数
     *
     * @param aid 漫画章节 ID
     * @param imgIndex 图片索引（文件名，不含扩展名）
     * @return 分段数（0 表示不需要解密）
     */
    fun getScrambleNum(aid: Int, imgIndex: String): Int {
        // 220980 之前的漫画没有混淆
        if (aid < JmConstants.SCRAMBLE_ID) {
            return 0
        }

        // 根据不同时期使用不同的算法
        val modulus = when {
            aid >= JmConstants.SCRAMBLE_421926 -> 8
            aid >= JmConstants.SCRAMBLE_268850 -> 10
            else -> return 10
        }

        // 计算分段数：2 * (MD5最后一位 % modulus) + 2
        val md5LastChar = md5LastCharCode("$aid$imgIndex")
        return 2 * (md5LastChar % modulus) + 2
    }

    /**
     * 获取 MD5 值最后一位字符的 ASCII 码
     *
     * @param input 输入字符串
     * @return 最后一位十六进制字符的 ASCII 码值
     */
    private fun md5LastCharCode(input: String): Int {
        val md5Hash = md5(input)
        return md5Hash.last().code
    }

    /**
     * 解密域名服务器返回的域名列表
     *
     * 域名服务器返回加密的 JSON 数据，解密流程：
     * 1. Base64 解码
     * 2. AES-ECB 解密（key = MD5(API_DOMAIN_SERVER_SECRET)）
     * 3. UTF-8 解码为文本
     * 4. 解析 JSON 并提取域名
     *
     * @param encryptedData 加密的 Base64 字符串
     * @return 域名列表
     * @throws Exception 解密失败时抛出异常
     */
    fun decryptDomainList(encryptedData: String): List<String> = decryptDomainOptions(encryptedData).map { it.domain }

    /**
     * 解密域名服务器返回的域名列表（保留显示标签）
     */
    fun decryptDomainOptions(encryptedData: String): List<DomainOption> {
        try {
            // 1. 生成 AES 密钥：MD5(API_DOMAIN_SERVER_SECRET)
            val keyString = md5(JmConstants.API_DOMAIN_SERVER_SECRET)
            val keyBytes = keyString.toByteArray(Charsets.UTF_8)
            val secretKey = SecretKeySpec(keyBytes, "AES")
            // 2. Base64 解码
            val encryptedBytes = Base64.decode(encryptedData, Base64.DEFAULT)
            // 3. AES-ECB 解密
            val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey)
            val decryptedBytes = cipher.doFinal(encryptedBytes)
            // 4. UTF-8 解码
            val decryptedText = String(decryptedBytes, Charsets.UTF_8)
            // 5. 解析 JSON 并提取域名+标签
            return extractDomainOptionsFromJson(decryptedText)
        } catch (e: Exception) {
            throw Exception("解密域名列表失败: ${e.message}", e)
        }
    }

    /**
     * 从 JSON 数据中提取域名
     *
     * 支持多种 JSON 格式：
     * - 简单数组：["domain1", "domain2"]
     * - 嵌套数组：[["domain1", "label1"], ["domain2", "label2"]]
     * - 对象格式：{"key": ["domain1", "domain2"]} 或 {"key": [["domain1", "label1"]]}
     */
    private fun extractDomainOptionsFromJson(jsonText: String): List<DomainOption> {
        val domains = mutableListOf<DomainOption>()
        try {
            val json = org.json.JSONObject(jsonText)
            // 遍历所有键
            json.keys().forEach { key ->
                val value = json.get(key)
                extractDomainOptionsFromValue(value, domains)
            }
        } catch (e: Exception) {
            // 如果不是对象，尝试作为数组解析
            try {
                val jsonArray = org.json.JSONArray(jsonText)
                extractDomainOptionsFromValue(jsonArray, domains)
            } catch (e2: Exception) {
                // 如果都失败，按行分割作为后备方案
                jsonText.lines()
                    .map { it.trim().removeSurrounding("\"") }
                    .filter { isLikelyDomain(it) }
                    .forEach { domains.add(DomainOption(domain = it)) }
            }
        }
        return mergeDomainOptions(domains)
    }

    /**
     * 递归提取域名
     */
    private fun extractDomainOptionsFromValue(
        value: Any,
        domains: MutableList<DomainOption>,
        inheritedLabel: String? = null,
    ) {
        when (value) {
            is org.json.JSONArray -> {
                for (i in 0 until value.length()) {
                    val item = value.get(i)
                    when (item) {
                        is String -> {
                            // 简单字符串，检查是否是域名
                            if (isLikelyDomain(item)) {
                                domains.add(DomainOption(domain = item, label = inheritedLabel))
                            }
                        }
                        is org.json.JSONArray -> {
                            // 嵌套数组 ["domain", "label"]，取第一个元素
                            if (item.length() > 0) {
                                val first = item.get(0)
                                if (first is String && isLikelyDomain(first)) {
                                    val label = buildList {
                                        val second = if (item.length() > 1) item.opt(1) else null
                                        if (second is String) add(second)
                                        if (!inheritedLabel.isNullOrBlank()) add(inheritedLabel)
                                    }.firstOrNull { !sanitizeLabel(it).isNullOrBlank() }
                                    domains.add(DomainOption(domain = first, label = sanitizeLabel(label)))
                                } else {
                                    extractDomainOptionsFromValue(item, domains, inheritedLabel)
                                }
                            }
                        }
                        is org.json.JSONObject -> extractDomainOptionsFromObject(item, domains, inheritedLabel)
                        else -> extractDomainOptionsFromValue(item, domains, inheritedLabel)
                    }
                }
            }
            is String -> {
                if (isLikelyDomain(value)) {
                    domains.add(DomainOption(domain = value, label = inheritedLabel))
                }
            }
            is org.json.JSONObject -> extractDomainOptionsFromObject(value, domains, inheritedLabel)
        }
    }

    private fun extractDomainOptionsFromObject(
        obj: org.json.JSONObject,
        domains: MutableList<DomainOption>,
        inheritedLabel: String? = null,
    ) {
        val directDomain = sequenceOf("domain", "host", "url", "api_domain")
            .mapNotNull { key -> obj.opt(key) as? String }
            .firstOrNull { isLikelyDomain(it) }
        val directLabel = sequenceOf("label", "name", "title", "desc", "line", "region")
            .mapNotNull { key -> obj.opt(key) as? String }
            .map { sanitizeLabel(it) }
            .firstOrNull { !it.isNullOrBlank() }
        if (directDomain != null) {
            domains.add(DomainOption(directDomain, directLabel ?: inheritedLabel))
            return
        }

        obj.keys().forEach { key ->
            val child = obj.get(key)
            val nextLabel = when {
                isLikelyDomain(key) -> inheritedLabel
                else -> sanitizeLabel(key) ?: inheritedLabel
            }
            extractDomainOptionsFromValue(child, domains, nextLabel)
        }
    }

    private fun mergeDomainOptions(items: List<DomainOption>): List<DomainOption> {
        val merged = LinkedHashMap<String, String?>()
        items.forEach { item ->
            val domain = item.domain.trim().removeSurrounding("\"")
            if (!isLikelyDomain(domain)) return@forEach

            val label = sanitizeLabel(item.label)
            val existing = merged[domain]
            if (existing.isNullOrBlank() || (!label.isNullOrBlank() && existing == domain)) {
                merged[domain] = label
            } else if (!merged.containsKey(domain)) {
                merged[domain] = label
            }
        }

        return merged.map { (domain, label) -> DomainOption(domain = domain, label = label) }
    }

    private fun sanitizeLabel(value: String?): String? {
        val normalized = value?.trim()?.removeSurrounding("\"")
        return normalized?.takeIf { it.isNotBlank() }
    }

    private fun isLikelyDomain(value: String): Boolean {
        val normalized = value.trim().removeSurrounding("\"")
        return normalized.contains('.') && !normalized.contains(':') && !normalized.contains('/')
    }
}

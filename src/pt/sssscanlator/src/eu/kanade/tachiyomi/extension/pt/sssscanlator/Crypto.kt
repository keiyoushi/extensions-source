package eu.kanade.tachiyomi.extension.pt.sssscanlator

import keiyoushi.lib.cryptoaes.CryptoAES

internal class PayloadException : Exception("Não foi possível descriptografar a resposta do site")

internal class PayloadScheme(
    val prefix: String,
    val reversed: Boolean,
    val passphrase: String,
)

internal object PayloadCipher {

    @Volatile
    var scheme = PayloadScheme(prefix = "YOMU_", reversed = true, passphrase = "yomu_trolling_scrapers_v3")

    fun isEncrypted(value: String) = value.startsWith(scheme.prefix)

    fun decrypt(value: String): String {
        val scheme = scheme
        val payload = value.removePrefix(scheme.prefix)
            .let { if (scheme.reversed) it.reversed() else it }

        return CryptoAES.decrypt(payload, scheme.passphrase).ifEmpty { throw PayloadException() }
    }

    /** Reads the scheme straight from the site's own decrypt helper, which changes every few weeks. */
    fun schemeFrom(script: String): PayloadScheme? {
        val call = script.indexOf(DECRYPT_CALL)
        if (call < 0) return null

        val helper = script.substring(maxOf(0, call - HELPER_LENGTH), call + DECRYPT_CALL.length + ARGUMENTS_LENGTH)
        val prefix = PREFIX_REGEX.find(helper)?.groupValues?.drop(1)?.firstOrNull(String::isNotEmpty) ?: return null

        return PayloadScheme(
            prefix = prefix,
            reversed = "reverse()" in helper,
            passphrase = passphraseFrom(helper) ?: return null,
        )
    }

    /** The passphrase wraps a salt between two literals, either by concatenation or interpolation. */
    private fun passphraseFrom(helper: String): String? {
        val variable = PASSPHRASE_REGEX.find(helper)?.groupValues?.get(1) ?: return null
        val assignment = Regex("""\b$variable\s*=\s*(.+)""").find(helper)?.groupValues?.get(1) ?: return null
        val (head, salt, tail) = CONCATENATION_REGEX.find(assignment)?.destructured
            ?: INTERPOLATION_REGEX.find(assignment)?.destructured
            ?: return null

        return head + saltFrom(salt, helper) + tail
    }

    /** The salt comes from a build variable that always falls back to a literal. */
    private fun saltFrom(salt: String, helper: String): String {
        val expression = when {
            '"' in salt -> salt
            else -> Regex("""\b$salt\s*=\s*([^,;]+)""").find(helper)?.groupValues?.get(1) ?: return ""
        }

        return LITERAL_REGEX.findAll(expression).lastOrNull()?.groupValues?.get(1).orEmpty()
    }

    private const val DECRYPT_CALL = "AES.decrypt("
    private const val HELPER_LENGTH = 600
    private const val ARGUMENTS_LENGTH = 32

    private val PREFIX_REGEX = """"([^"]+)"\s*===?\s*\w+\.(?:substring|slice)\(|\.startsWith\("([^"]+)"\)""".toRegex()
    private val PASSPHRASE_REGEX = """AES\.decrypt\(\s*\w+\s*,\s*(\w+)\s*\)""".toRegex()
    private val CONCATENATION_REGEX = """"([^"]*)"\s*\+\s*([\w.]+)\s*\+\s*"([^"]*)"""".toRegex()
    private val INTERPOLATION_REGEX = $$"""`([^`$]*)\$\{([^}]+)\}([^`]*)`""".toRegex()
    private val LITERAL_REGEX = """"([^"]*)"""".toRegex()
}

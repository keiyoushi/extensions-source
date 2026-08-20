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
    var scheme = PayloadScheme(prefix = "YOMU_", reversed = true, passphrase = "yomu_trolling_scrapers_v2")

    fun isEncrypted(value: String) = value.startsWith(scheme.prefix)

    fun decrypt(value: String): String {
        val scheme = scheme
        val payload = value.removePrefix(scheme.prefix)
            .let { if (scheme.reversed) it.reversed() else it }

        return CryptoAES.decrypt(payload, scheme.passphrase).ifEmpty { throw PayloadException() }
    }

    /** Reads the scheme straight from the site's own decrypt helper, which changes every few weeks. */
    fun schemeFrom(script: String): PayloadScheme? {
        val (prefix, body, salt, passphrase) = SCHEME_REGEX.find(script)?.destructured ?: return null

        return PayloadScheme(
            prefix = prefix,
            reversed = "reverse" in body,
            passphrase = TEMPLATE_REGEX.replace(passphrase, salt),
        )
    }

    private val SCHEME_REGEX = """startsWith\("([^"]+)"\)(.{0,160}?)\|\|"([^"]*)"[^`]{0,40}`([^`]+)`""".toRegex()
    private val TEMPLATE_REGEX = $$"""\$\{[^}]*\}""".toRegex()
}

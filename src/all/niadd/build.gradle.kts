plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Niadd"
    versionCode = 2
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    val subdomains = mapOf(
        "pt-BR" to "br",
        "en" to "www",
        "es" to "es",
        "it" to "it",
        "ru" to "ru",
        "de" to "de",
        "fr" to "fr",
    )
    subdomains.forEach { (langCode, sub) ->
        source {
            lang = langCode
            baseUrl = "https://$sub.niadd.com"
        }
    }
}

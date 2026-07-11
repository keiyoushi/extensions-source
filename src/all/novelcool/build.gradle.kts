plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "NovelCool"
    versionCode = 8
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    val subdomains = mapOf(
        "en" to "www",
        "es" to "es",
        "de" to "de",
        "ru" to "ru",
        "it" to "it",
        "pt-BR" to "br",
        "fr" to "fr",
    )
    subdomains.forEach { (langCode, sub) ->
        source {
            lang = langCode
            baseUrl = "https://$sub.novelcool.com"
        }
    }
}

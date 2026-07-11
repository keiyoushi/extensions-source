plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Traducciones Moonlight"
    versionCode = 46
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "moonlighttl"

    source {
        lang = "es"
        baseUrl = "https://traduccionesmoonlight.com"
        versionId = 3
    }
}

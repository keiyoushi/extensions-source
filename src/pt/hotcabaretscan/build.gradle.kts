plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Hot Cabaret Scan"
    versionCode = 1
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "pt-BR"
        baseUrl = "https://hotcabaretscan.com"
    }
}

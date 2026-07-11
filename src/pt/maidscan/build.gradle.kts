plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Maid Scan"
    versionCode = 51
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "greenshit"

    source {
        lang = "pt-BR"
        baseUrl = "https://empreguetes.wtf"
    }
}

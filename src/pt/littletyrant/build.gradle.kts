plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Little Tyrant"
    versionCode = 10
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "pt-BR"
        baseUrl = "https://tiraninha.world"
    }
}

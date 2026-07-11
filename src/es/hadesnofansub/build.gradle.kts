plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Hades no Fansub"
    versionCode = 5
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "es"
        baseUrl = "https://lectorhades.latamtoon.com"
    }
}

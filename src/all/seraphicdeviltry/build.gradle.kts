plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "SeraphicDeviltry"
    versionCode = 2
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "en"
        baseUrl = "https://seraphic-deviltry.com"
    }

    source {
        lang = "es"
        baseUrl = "https://spanish.seraphic-deviltry.com"
    }
}

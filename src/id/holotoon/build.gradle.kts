plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Holotoon"
    versionCode = 0
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "id"
        baseUrl = "https://01.holotoon.site"
    }
}

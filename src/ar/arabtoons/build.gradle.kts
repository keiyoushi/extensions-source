plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Arab Toons"
    versionCode = 1
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "madara"

    source {
        name = "عرب تونز"
        lang = "ar"
        baseUrl = "https://arabtoons.net"
    }
}

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "18Manhua"
    versionCode = 0
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "goda"

    source {
        name = "18漫画"
        lang = "zh"
        baseUrl = "https://18mh.org"
    }
}

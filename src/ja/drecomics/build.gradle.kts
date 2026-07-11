plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "DreComi+"
    versionCode = 3
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "ja"
        baseUrl = "https://drecomi-plus.jp"
        versionId = 2
    }
}

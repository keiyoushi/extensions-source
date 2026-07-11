plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "WebtoonXYZ"
    versionCode = 4
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "en"
        baseUrl = "https://www.webtoon.xyz"
    }
}

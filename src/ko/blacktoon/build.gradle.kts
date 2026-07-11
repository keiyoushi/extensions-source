plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "BlackToon"
    versionCode = 1
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        name = "블랙툰"
        lang = "ko"
        baseUrl = "https://blacktoon.me"
    }
}

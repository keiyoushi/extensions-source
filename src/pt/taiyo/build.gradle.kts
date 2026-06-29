plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Taiyō"
    className = "Taiyo"
    versionCode = 11
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    deeplink {
        host("taiyo.moe")
        path("/media/..*")
    }
}

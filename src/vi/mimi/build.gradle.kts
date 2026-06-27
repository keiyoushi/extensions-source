plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MiMi"
    className = "MiMi"
    versionCode = 6
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    deeplink {
        host("mimimoe.moe")
        path("/manga/..*")
    }
}

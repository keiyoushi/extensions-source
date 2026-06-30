plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MiMi"
    versionCode = 6
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "vi"
        baseUrl = "https://mimimoe.moe"
    }

    deeplink {
        host("mimimoe.moe")
        path("/manga/..*")
    }
}

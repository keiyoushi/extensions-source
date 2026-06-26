plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Hachiraw"
    className = "Hachiraw"
    versionCode = 3
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    deeplink {
        host("hachiraw.net")
        path("/manga/..*")
    }
}

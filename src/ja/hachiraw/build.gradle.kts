plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Hachiraw"
    versionCode = 3
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "ja"
        baseUrl = "https://hachiraw.net"
    }

    deeplink {
        host("hachiraw.net")
        path("/manga/..*")
    }
}

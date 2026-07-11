plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Hachiraw"
    versionCode = 3
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
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

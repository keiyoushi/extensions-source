plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Pixiv"
    versionCode = 12
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    listOf("en", "ja", "zh", "zh-tw", "ko").forEach {
        source {
            lang = it
            baseUrl = "https://www.pixiv.net"
        }
    }

    deeplink {
        host("pixiv.net")
        host("www.pixiv.net")
        path("/en/artworks/..*")
        path("/artworks/..*")
        path("/en/users/..*")
        path("/users/..*")
        path("/user/..*/series/..*")
    }
}

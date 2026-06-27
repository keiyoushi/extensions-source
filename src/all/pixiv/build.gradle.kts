plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Pixiv"
    className = "PixivFactory"
    versionCode = 12
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

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

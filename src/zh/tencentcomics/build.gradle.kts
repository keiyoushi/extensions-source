plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Tencent Comics (ac.qq.com)"
    className = "TencentComics"
    versionCode = 10
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    deeplink {
        host("ac.qq.com")
        host("*.ac.qq.com")
        path("/comic/index/id/..*")
        path("/Comic/comicInfo/id/..*")
    }
}

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Tencent Comics (ac.qq.com)"
    versionCode = 10
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        name = "腾讯动漫"
        lang = "zh-Hans"
        baseUrl = "https://m.ac.qq.com"
        id = 6353436350537369479L
    }

    deeplink {
        host("ac.qq.com")
        host("*.ac.qq.com")
        path("/comic/index/id/..*")
        path("/Comic/comicInfo/id/..*")
    }
}

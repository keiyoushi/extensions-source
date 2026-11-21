import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Xsnvshen"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        name = "秀色女神"
        lang = "zh"
        baseUrl = "https://m.xsnvshen.com"
    }

    deeplink {
        host("m.xsnvshen.com")
        host("www.xsnvshen.com")
        host("xsnvshen.com")
        path("/album/..*")
        path("/girl/..*")
    }
}

import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MyComic"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "zh"
        baseUrl = "https://mycomic.com"
    }

    deeplink {
        path("/comics/..*")
        path("/cn/comics/..*")
        path("/chapters/..*")
        path("/cn/chapters/..*")
    }
}

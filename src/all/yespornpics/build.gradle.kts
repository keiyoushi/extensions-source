import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "YesPornPics"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "all"
        baseUrl = "https://yespornpics.com"
    }

    deeplink {
        host("yespornpics.com")
        host("www.yespornpics.com")
        path("/sex/..*")
    }
}

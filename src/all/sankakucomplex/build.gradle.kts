import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Sankaku Complex"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        baseUrl = "https://sankaku.app"
        lang = "all"
    }

    deeplink {
        host("sankaku.app")
        host("www.sankakucomplex.com")
        path("/books/..*")
        path("/series/..*")
    }
}

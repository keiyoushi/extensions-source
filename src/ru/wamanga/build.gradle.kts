import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "WaManga"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "ru"
        baseUrl = "https://wamanga.ru"
    }

    deeplink {
        host("wamanga.ru")
        path("/manga/..*")
        path("/manhwa/..*")
        path("/manhua/..*")
        path("/comic/..*")
        path("/manuscript/..*")
    }
}

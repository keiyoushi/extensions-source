import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaBuff"
    versionCode = 7
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "ru"
        baseUrl = "https://mangabuff.ru"
    }

    deeplink {
        host("mangabuff.ru")
        path("/manga/..*")
    }
}

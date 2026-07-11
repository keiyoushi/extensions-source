import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaBuff"
    versionCode = 6
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "ru"
        baseUrl = "https://mangabuff.ru"
    }

    deeplink {
        host("mangabuff.ru")
        path("/manga/..*")
    }
}

import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "AchyManga"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "es"
        baseUrl = "https://achymanga.online"
    }

    source {
        lang = "en"
        baseUrl = "https://achymanga.online"
    }

    deeplink {
        host("achymanga.online")
        host("www.achymanga.online")
        path("/manga/..*")
    }
}

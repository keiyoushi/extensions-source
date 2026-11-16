import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Gcomics"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "es"
        baseUrl = "https://gcomics.online"
    }

    deeplink {
        host("gcomics.online")
        host("www.gcomics.online")
        path("/serie/..*")
        path("/webtoon/..*")
        path("/comic/..*")
    }
}

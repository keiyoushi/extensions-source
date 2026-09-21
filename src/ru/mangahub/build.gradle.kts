import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Mangahub"
    versionCode = 23
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        baseUrl {
            custom("https://mangahub.ru")
        }
        lang = "ru"
    }

    deeplink {
        path("/title/..*")
    }
}

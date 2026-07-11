import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Desu"
    versionCode = 33
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        baseUrl {
            custom("https://desu.uno")
        }
        lang = "ru"
        id = 6684416167758830305L
    }

    deeplink {
        host("desu.uno")
        path("/manga/..*")
    }
}

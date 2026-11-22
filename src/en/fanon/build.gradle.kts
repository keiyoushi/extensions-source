import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Fanon"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        baseUrl = "https://www.fanon.co"
        lang = "en"
    }

    deeplink {
        host("www.fanon.co")
        host("fanon.co")
        path("/fanfics/..*")
    }
}

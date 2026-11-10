import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "El Vosque"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "es"
        baseUrl = "https://elvosque.es"
    }

    deeplink {
        host("elvosque.es")
        host("www.elvosque.es")
        path("/archive/..*")
        path("/comic/..*")
    }
}

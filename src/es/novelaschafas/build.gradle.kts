import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Novelas Chafas"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "es"
        baseUrl = "https://novelasmaschafas.blogspot.com"
    }

    deeplink {
        path("/p/..*")
        path("/20..*")
    }
}

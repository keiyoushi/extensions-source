import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Panda Suki"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "pt-BR"
        baseUrl = "https://pandasuki.net"
    }

    deeplink {
        host("pandasuki.net")
        host("www.pandasuki.net")
        path("/manhwa/..*")
        path("/capitulo/..*")
        path("/genero/..*")
    }
}

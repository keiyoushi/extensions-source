import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "AnimeBbg"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "es"
        baseUrl = "https://animebbg.net"
    }

    deeplink {
        host("animebbg.net")
        host("www.animebbg.net")
        path("/obras/..*")
        path("/genero/..*")
    }
}

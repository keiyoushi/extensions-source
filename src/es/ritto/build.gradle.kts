import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Ritto"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "es"
        baseUrl = "https://ritto.cc"
    }

    deeplink {
        path("/obra/..*")
        path("/scan/..*")
        path("/autor/..*")
        path("/catalogo")
    }
}

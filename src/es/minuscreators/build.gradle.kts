import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Minus Creators"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "es"
        baseUrl = "https://minuscreators.com"
    }

    deeplink {
        host("minuscreators.com")
        host("www.minuscreators.com")
        path("/obra/..*")
        path("/perfil/..*")
        path("/categorias")
    }
}

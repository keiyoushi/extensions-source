import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Funktoon"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "pt-BR"
        baseUrl = "https://funktoon.com"
    }

    deeplink {
        host("funktoon.com")
        host("www.funktoon.com")
        path("/serie/..*")
        path("/categorias/..*")
        path("/vitrine/..*")
        path("/perfil/..*")
    }
}

import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Quadrinhos de Sexo"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        baseUrl = "https://www.quadrinhosdesexo.com"
        lang = "pt-BR"
    }

    deeplink {
        path("/..*")
    }
}

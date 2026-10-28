import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "SkyMangas"
    versionCode = 2
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "es"
        baseUrl = "https://skymangas.com"
        versionId = 2
    }

    deeplink {
        path("/manhua/..*")
        path("/leer/..*")
        path("/autores/..*")
        path("/artistas/..*")
        path("/generos/..*")
        path("/genero/..*")
        path("/tags/..*")
    }
}

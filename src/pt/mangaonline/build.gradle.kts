import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manga Online"
    versionCode = 59
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "pt-BR"
        baseUrl = "https://mangaonline.love"
        versionId = 3
    }

    deeplink {
        path("/manga/..*")
        path("/capitulo/..*")
        path("/genre/..*")
    }
}

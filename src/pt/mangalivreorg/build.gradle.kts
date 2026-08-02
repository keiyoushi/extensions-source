import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaLivre.org"
    versionCode = 2
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "pt-BR"
        baseUrl = "https://mangalivre.org"
    }

    deeplink {
        host("mangalivre.org")
        path("/manga/..*")
        path("/ler/..*")
    }
}

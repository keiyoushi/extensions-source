import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Hot Manga"
    theme = "komiko"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "fr"
        baseUrl = "https://www.hot-manga.fr"
    }

    deeplink {
        host("www.hot-manga.fr")
        host("hot-manga.fr")
        path("/produit/..*")
        path("/lecteur-ebook/..*")
    }
}

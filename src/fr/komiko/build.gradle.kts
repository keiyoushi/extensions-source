import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Komiko"
    theme = "komiko"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "fr"
        baseUrl = "https://www.komiko.io"
    }

    deeplink {
        host("www.komiko.io")
        host("komiko.io")
        path("/catalogue/produit/..*")
        path("/lecteur-ebook/..*")
    }
}

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Kawii Manga"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "ar"
        baseUrl = "https://kawaiimanga.org"
    }

    deeplink {
        host("kawaiimanga.org")
        path("/.*/..*")
    }
}

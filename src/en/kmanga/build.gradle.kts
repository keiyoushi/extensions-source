plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "K Manga"
    className = "KManga"
    versionCode = 5
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    deeplink {
        host("kmanga.kodansha.com")
        path("/title/..*")
    }
}

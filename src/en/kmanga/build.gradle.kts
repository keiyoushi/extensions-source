plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "K Manga"
    versionCode = 5
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://kmanga.kodansha.com"
    }

    deeplink {
        host("kmanga.kodansha.com")
        path("/title/..*")
    }
}

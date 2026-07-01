plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "New Manhwa"
    versionCode = 34
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl("https://newmanhwa.com") {
            mirrors = listOf("https://fullmanhwa.com")
        }
    }

    deeplink {
        host("newmanhwa.com")
        host("fullmanhwa.com")
        path("/..*")
    }
}

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "New Manhwa"
    versionCode = 34
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl {
            mirrors(
                "https://newmanhwa.com",
                "https://fullmanhwa.com",
            )
        }
    }

    deeplink {
        host("newmanhwa.com")
        host("fullmanhwa.com")
        path("/..*")
    }
}

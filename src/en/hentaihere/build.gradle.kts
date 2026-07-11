plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "HentaiHere"
    versionCode = 7
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://hentaihere.com"
    }

    deeplink {
        host("hentaihere.com")
        path("/m/S..*")
    }
}

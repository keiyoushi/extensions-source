plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "HentaiHere"
    className = "HentaiHere"
    versionCode = 7
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    deeplink {
        host("hentaihere.com")
        path("/m/S..*")
    }
}

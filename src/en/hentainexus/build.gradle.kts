plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "HentaiNexus"
    className = "HentaiNexus"
    versionCode = 18
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    deeplink {
        host("hentainexus.com")
        path("/view/..*")
    }
}

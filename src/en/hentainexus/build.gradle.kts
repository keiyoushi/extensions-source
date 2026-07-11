plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "HentaiNexus"
    versionCode = 18
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://hentainexus.com"
    }

    deeplink {
        host("hentainexus.com")
        path("/view/..*")
    }
}

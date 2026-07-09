plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "LeerMangaEsp"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "es"
        baseUrl = "https://leermangaesp.net"
    }

    deeplink {
        host("leermangaesp.net")
        path("/manga/..*")
        path("/leer-m/..*")
    }
}

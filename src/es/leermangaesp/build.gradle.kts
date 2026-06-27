plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "LeerMangaEsp"
    className = "LeerMangaEsp"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    deeplink {
        host("leermangaesp.net")
        path("/manga/..*")
        path("/leer-m/..*")
    }
}

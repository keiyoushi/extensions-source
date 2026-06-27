plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Comix"
    className = "Comix"
    versionCode = 31
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    deeplink {
        host("comix.to")
        host("www.comix.to")
        path("/title/..*")
    }
}

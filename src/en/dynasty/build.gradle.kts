plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Dynasty"
    className = "Dynasty"
    versionCode = 30
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    deeplink {
        host("dynasty-scans.com")
        host("*.dynasty-scans.com")
        path("/anthologies/..*")
        path("/chapters/..*")
        path("/doujins/..*")
        path("/issues/..*")
        path("/series/..*")
    }
}

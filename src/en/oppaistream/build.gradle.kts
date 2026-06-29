plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Oppai Stream"
    className = "OppaiStream"
    versionCode = 5
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    deeplink {
        host("read.oppai.stream")
        path("/manhwa")
        path("/page")
    }
}

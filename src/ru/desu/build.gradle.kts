plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Desu"
    className = "Desu"
    versionCode = 33
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    deeplink {
        host("desu.me")
        host("desu.win")
        path("/manga/..*")
    }
}

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Luscious"
    className = "LusciousFactory"
    versionCode = 32
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    deeplink {
        host("www.luscious.net")
        host("members.luscious.net")
        path("/albums/..*")
    }
}

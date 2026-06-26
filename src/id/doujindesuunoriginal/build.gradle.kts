plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "DoujinDesu (Unoriginal)"
    className = "DoujinDesuUnoriginal"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    deeplink {
        host("v2.doujindesu.fun")
        path("/manga/..*")
    }
}

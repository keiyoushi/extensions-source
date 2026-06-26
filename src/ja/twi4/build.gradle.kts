plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Twi4"
    className = "Twi4"
    versionCode = 6
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    deeplink {
        host("sai-zen-sen.jp")
        path("/comics/twi4/..*/.*")
    }
}

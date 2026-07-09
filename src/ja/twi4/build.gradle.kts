plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Twi4"
    versionCode = 7
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "ja"
        // The domain sai-zen-sen.jp directs to their main site rather than Twi4. It has to be /comics/twi4
        baseUrl = "https://sai-zen-sen.jp/comics/twi4"
    }

    deeplink {
        path("/comics/twi4/..*/.*")
    }
}

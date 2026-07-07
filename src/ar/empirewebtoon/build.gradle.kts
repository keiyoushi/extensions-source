plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Empire Webtoon"
    versionCode = 6
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "ar"
        baseUrl {
            custom("https://webtoonempire-bl.com")
        }
    }
}

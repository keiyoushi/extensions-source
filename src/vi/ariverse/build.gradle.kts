plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Ariverse"
    versionCode = 53
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "vi"
        baseUrl {
            custom("https://arigl.xyz")
        }
        id = 4480433466073326866
    }
}

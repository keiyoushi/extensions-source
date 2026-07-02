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
        baseUrl("https://arigl.xyz") {
            withCustom = true
        }
        id = 4480433466073326866
    }
}

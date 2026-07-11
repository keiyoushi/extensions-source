plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Ariverse"
    versionCode = 53
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "vi"
        baseUrl {
            custom("https://arigl.xyz")
        }
        id = 4480433466073326866
    }
}

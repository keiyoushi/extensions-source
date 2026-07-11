plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "LuvEvaLand"
    versionCode = 2
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "vi"
        baseUrl {
            custom("https://luvevalands2.co")
        }
    }
}

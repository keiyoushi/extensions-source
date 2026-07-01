plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "LuvEvaLand"
    versionCode = 2
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "vi"
        baseUrl("https://luvevalands2.co") {
            withCustom = true
        }
    }
}

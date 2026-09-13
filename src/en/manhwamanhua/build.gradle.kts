import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ManhwaManhua"
    versionCode = 0
    contentWarning = ContentWarning.NSFW // or MIXED, please confirm
    libVersion = "1.6"
    theme = "madara"

    source {
        lang = "en"
        baseUrl = "https://manhwamanhua.com"
    }
}

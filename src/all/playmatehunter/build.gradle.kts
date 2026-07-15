import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Playmate Hunter"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "masonry"

    source {
        lang = "all"
        baseUrl = "https://pmatehunter.com"
    }
}

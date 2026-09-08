import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Mikrokosmos Fansub"
    versionCode = 2
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"
    theme = "zeistmanga"

    source {
        lang = "tr"
        baseUrl = "https://mikrokosmosfb.blogspot.com"
    }
}

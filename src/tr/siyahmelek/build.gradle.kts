import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Siyah Melek"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"
    theme = "initmanga"

    source {
        lang = "tr"
        baseUrl {
            custom("https://siyahmelek.live")
        }
        versionId = 2
    }
}

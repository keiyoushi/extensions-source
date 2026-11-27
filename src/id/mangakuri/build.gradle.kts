import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Mushinook"
    versionCode = 36
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"
    theme = "loneseal"

    source {
        lang = "id"
        baseUrl = "https://mushinook.org"
        id = 8634079728231059906L
    }
}

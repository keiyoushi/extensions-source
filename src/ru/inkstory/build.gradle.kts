import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "InkStory"
    versionCode = 4
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "ru"
        baseUrl = "https://inkstory.net"
    }
}

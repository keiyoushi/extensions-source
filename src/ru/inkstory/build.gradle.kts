import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "InkStory"
    versionCode = 5
    contentWarning = ContentWarning.MIXED
    libVersion = "1.4"

    source {
        lang = "ru"
        baseUrl = "https://inkstory.net"
    }
}

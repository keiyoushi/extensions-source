import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "KissLove"
    versionCode = 19
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "ja"
        baseUrl = "https://klz9.com"
    }
}

dependencies {

    api(project(":lib:i18n"))
}

import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Toonily.me"
    versionCode = 2
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "en"
        baseUrl = "https://toontop.io"
    }
}

dependencies {
    implementation(project(":lib-multisrc:mangak"))
}

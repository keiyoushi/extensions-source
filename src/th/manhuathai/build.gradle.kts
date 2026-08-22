import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ManhuaThai"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "madaralegacy"

    source {
        lang = "th"
        baseUrl = "https://www.manhuathai.com"
    }
}

dependencies {

    implementation(project(":lib:unpacker"))
}

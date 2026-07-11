import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Kiraboshi"
    versionCode = 3
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "ja"
        baseUrl = "https://kirapo.jp"
        versionId = 2
    }
}

dependencies {

    implementation(project(":lib:speedbinb"))
}

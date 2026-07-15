import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Ohta Web Comic"
    versionCode = 3
    contentWarning = ContentWarning.MIXED
    libVersion = "1.4"

    source {
        lang = "ja"
        baseUrl = "https://webcomic.ohtabooks.com"
    }
}

dependencies {

    implementation(project(":lib:speedbinb"))
}

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Ohta Web Comic"
    versionCode = 3
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "ja"
        baseUrl = "https://webcomic.ohtabooks.com"
    }
}

dependencies {

    implementation(project(":lib:speedbinb"))
}

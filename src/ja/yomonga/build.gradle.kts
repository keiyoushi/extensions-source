plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Yomonga"
    versionCode = 2
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "ja"
        baseUrl = "https://www.yomonga.com"
    }
}

dependencies {

    implementation(project(":lib:speedbinb"))
}

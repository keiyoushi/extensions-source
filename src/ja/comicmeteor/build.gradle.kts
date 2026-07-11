plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Kiraboshi"
    versionCode = 3
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
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

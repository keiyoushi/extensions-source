plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Comico"
    versionCode = 2
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        name = "コミコ"
        lang = "ja"
        baseUrl = "https://www.comico.jp"
    }
}

dependencies {

    implementation(project(":lib:cryptoaes"))
}

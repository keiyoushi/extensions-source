plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Comic Nettai"
    versionCode = 2
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "ja"
        baseUrl = "https://www.comicnettai.com"
    }
}

dependencies {

    implementation(project(":lib:publus"))
}

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Creative Comic Collection"
    versionCode = 2
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        name = "CCC追漫台"
        lang = "zh-Hant"
        baseUrl = "https://www.creative-comic.tw"
    }
}

dependencies {

    implementation(project(":lib:cryptoaes"))
}

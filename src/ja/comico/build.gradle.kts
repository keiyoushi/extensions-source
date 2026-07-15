import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Comico"
    versionCode = 2
    contentWarning = ContentWarning.MIXED
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

import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Gaugau Monster Plus"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        name = "がうがうモンスター＋"
        lang = "ja"
        baseUrl = "https://gaugau.futabanet.jp"
    }
}

dependencies {
    implementation(project(":lib:speedbinb"))
}

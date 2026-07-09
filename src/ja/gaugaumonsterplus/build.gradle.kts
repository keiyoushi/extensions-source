plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Gaugau Monster Plus"
    versionCode = 3
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        name = "がうがうモンスター＋"
        lang = "ja"
        baseUrl = "https://gaugau.futabanet.jp"
    }
}

dependencies {

    implementation(project(":lib:speedbinb"))
}

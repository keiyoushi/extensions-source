plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Weekly Young Magazine"
    versionCode = 5
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        name = "ヤンマガ（マンガ）"
        lang = "ja"
        baseUrl = "https://yanmaga.jp"
    }

    source {
        name = "ヤンマガ（グラビア）"
        lang = "ja"
        baseUrl = "https://yanmaga.jp"
    }
}

dependencies {
    implementation(project(":lib:speedbinb"))
}

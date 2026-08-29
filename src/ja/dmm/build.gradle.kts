import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "DMM/FANZA"
    versionCode = 4
    contentWarning = ContentWarning.MIXED
    libVersion = "1.4"

    source {
        name = "DMM"
        lang = "ja"
        baseUrl = "https://book.dmm.com"
    }

    source {
        name = "FANZA"
        lang = "ja"
        baseUrl = "https://book.dmm.co.jp"
    }
}

dependencies {
    implementation(project(":lib:publus"))
}

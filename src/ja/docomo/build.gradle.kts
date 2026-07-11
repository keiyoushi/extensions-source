import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Docomo"
    versionCode = 2
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "ja"
        baseUrl = "https://dbook.docomo.ne.jp"
    }
}

dependencies {

    implementation(project(":lib:publus"))
}

import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "KadoComi"
    versionCode = 2
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        name = "カドコミ"
        lang = "ja"
        baseUrl = "https://comic-walker.com"
    }
}

dependencies {
    implementation(project(":lib:xorinterceptor"))
}

import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manben"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        name = "漫本"
        lang = "zh"
        baseUrl = "https://www.manben.com"
    }

    deeplink {
        host("www.manben.com")
        host("manben.com")
        path("/m..*")
    }
}

dependencies {
    implementation(project(":lib:unpacker"))
}

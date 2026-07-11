import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Komga"
    versionCode = 68
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        name = "Komga"
        lang = "all"
        baseUrl = "https://127.0.0.1:25600"
        id = 4508733312114627536L
    }
    source {
        name = "Komga (2)"
        lang = "all"
        baseUrl = "https://127.0.0.1:25600"
        id = 8074481155021144106L
    }
    source {
        name = "Komga (3)"
        lang = "all"
        baseUrl = "https://127.0.0.1:25600"
        id = 5132811728275817394L
    }
}

dependencies {
    implementation("org.apache.commons:commons-text:1.11.0")
}

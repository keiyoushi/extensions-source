import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Twatt"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "fr"
        baseUrl = "https://twatt.fr"
    }
}

dependencies {

    implementation(project(":lib:dataimage"))
}

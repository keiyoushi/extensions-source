import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "buttsmithy"
    versionCode = 2
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        name = "Buttsmithy"
        lang = "en"
        baseUrl = "https://incase.buttsmithy.com"
    }
}

dependencies {

    implementation(project(":lib:textinterceptor"))
}

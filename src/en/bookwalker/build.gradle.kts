import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "BookWalker"
    versionCode = 7
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://bookwalker.com"
        id = 2744810059574599668L
    }
}

dependencies {

    implementation(project(":lib:e4p"))
}

import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "BookWalker Japan"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "ja"
        baseUrl = "https://bookwalker.jp"
    }
}

dependencies {
    implementation(project(":lib:publus"))
    implementation(project(":lib:cookieinterceptor"))
}

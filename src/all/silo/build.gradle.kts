import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Silo"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "all"
        baseUrl {
            custom("http://127.0.0.1:8090")
        }
    }
}

dependencies {
    implementation("com.github.gpanther:java-nat-sort:natural-comparator-1.1")
}

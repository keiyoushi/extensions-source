import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "VIZ"
    versionCode = 28
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    deeplink {
        host("www.viz.com")
        path("/..*/chapters/..*")
        path("/..*/..*/chapter/..*")
    }

    source {
        name = "VIZ Shonen Jump"
        lang = "en"
        baseUrl = "https://www.viz.com"
        versionId = 2
    }

    source {
        name = "VIZ Manga"
        lang = "en"
        baseUrl = "https://www.viz.com"
        versionId = 2
    }
}

dependencies {
    implementation("de.stefan-oltmann:kim:0.32.0") {
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-serialization-core")
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-serialization-json")
    }
}

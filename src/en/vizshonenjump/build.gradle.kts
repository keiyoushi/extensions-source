plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "VIZ"
    versionCode = 26
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
    }

    source {
        name = "VIZ Manga"
        lang = "en"
        baseUrl = "https://www.viz.com"
    }
}

dependencies {
    implementation("com.drewnoakes:metadata-extractor:2.18.0")
}

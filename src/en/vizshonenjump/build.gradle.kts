plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "VIZ"
    className = "VizFactory"
    versionCode = 25
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    deeplink {
        host("www.viz.com")
        path("/..*/chapters/..*")
        path("/..*/..*/chapter/..*")
    }
}

dependencies {

    implementation("com.drewnoakes:metadata-extractor:2.18.0")
}

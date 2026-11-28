plugins {
    alias(kei.plugins.multisrc)
}

keiyoushi {
    baseVersionCode = 5
    libVersion = "1.6"

    deeplink {
        path("/..*/user/..*")
    }
}

dependencies {
    implementation("com.github.gpanther:java-nat-sort:natural-comparator-1.1")
}

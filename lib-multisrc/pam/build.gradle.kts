plugins {
    alias(kei.plugins.multisrc)
}

dependencies {
    api(project(":lib:secretstream"))
}

keiyoushi {
    baseVersionCode = 2
    libVersion = "1.4"
}

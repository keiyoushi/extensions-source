
plugins {
    alias(kei.plugins.multisrc)
}

multisrc {
    baseVersionCode = 2
}

dependencies {
    api(project(":lib:secretstream"))
}

plugins {
    id("lib-multisrc")
}

baseVersionCode = 44

dependencies {
    api(project(":lib:cryptoaes"))
    api(project(":lib:i18n"))
}

plugins {
    id("lib-multisrc")
}

baseVersionCode = 37

dependencies {
    api(project(":lib:cryptoaes"))
    api(project(":lib:i18n"))
}

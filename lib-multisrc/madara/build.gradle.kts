plugins {
    id("lib-multisrc")
}

baseVersionCode = 41

dependencies {
    api(project(":lib:cryptoaes"))
    api(project(":lib:i18n"))
}

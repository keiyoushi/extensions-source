plugins {
    id("lib-multisrc")
}

baseVersionCode = 35

dependencies {
    api(project(":lib:cryptoaes"))
    api(project(":lib:i18n"))
}

plugins {
    id("lib-multisrc")
}

baseVersionCode = 42

dependencies {
    api(project(":lib:cryptoaes"))
    api(project(":lib:i18n"))
}

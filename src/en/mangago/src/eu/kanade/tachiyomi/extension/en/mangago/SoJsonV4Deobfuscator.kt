package eu.kanade.tachiyomi.extension.en.mangago

import kotlin.IllegalArgumentException

/*
    Ported from https://github.com/hax0r31337/JSDec/blob/master/js/dec.js

    SPDX-License-Identifier: MIT
    Copyright (c) 2020 liulihaocai
 */
object SoJsonV4Deobfuscator {
    private val splitRegex: Regex = Regex("""[a-zA-Z]+""")

    fun decode(jsf: String): String {
        if (!jsf.startsWith("['sojson.v4']")) {
            throw IllegalArgumentException("Obfuscated code is not sojson.v4")
        }

        val args = jsf.substring(240, jsf.length - 59).split(splitRegex)

        return args.map { it.toInt().toChar() }.joinToString("")
    }
}

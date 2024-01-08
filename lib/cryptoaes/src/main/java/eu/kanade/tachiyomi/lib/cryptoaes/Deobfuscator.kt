package eu.kanade.tachiyomi.lib.cryptoaes
/**
 * Helper class to deobfuscate JavaScript strings encoded in JSFuck style.
 *
 * More info on JSFuck found [here](https://en.wikipedia.org/wiki/JSFuck).
 *
 * Currently only supports Numeric and decimal ('.') characters
 */
object Deobfuscator {
    fun deobfuscateJsPassword(inputString: String): String {
        var idx = 0
        val brackets = listOf('[', '(')
        val evaluatedString = StringBuilder()
        while (idx < inputString.length) {
            val chr = inputString[idx]
            if (chr !in brackets) {
                idx++
                continue
            }
            val closingIndex = getMatchingBracketIndex(idx, inputString)
            if (chr == '[') {
                val digit = calculateDigit(inputString.substring(idx, closingIndex))
                evaluatedString.append(digit)
            } else {
                evaluatedString.append('.')
                if (inputString.getOrNull(closingIndex + 1) == '[') {
                    val skippingIndex = getMatchingBracketIndex(closingIndex + 1, inputString)
                    idx = skippingIndex + 1
                    continue
                }
            }
            idx = closingIndex + 1
        }
        return evaluatedString.toString()
    }

    private fun getMatchingBracketIndex(openingIndex: Int, inputString: String): Int {
        val openingBracket = inputString[openingIndex]
        val closingBracket = when (openingBracket) {
            '[' -> ']'
            else -> ')'
        }
        var counter = 0
        for (idx in openingIndex until inputString.length) {
            if (inputString[idx] == openingBracket) counter++
            if (inputString[idx] == closingBracket) counter--

            if (counter == 0) return idx // found matching bracket
            if (counter < 0) return -1 // unbalanced brackets
        }
        return -1 // matching bracket not found
    }

    private fun calculateDigit(inputSubString: String): Char {
        /* 0 == '+[]'
           1 == '+!+[]'
           2 == '!+[]+!+[]'
           3 == '!+[]+!+[]+!+[]'
           ...
           therefore '!+[]' count equals the digit
           if count equals 0, check for '+[]' just to be sure
         */
        val digit = "!\\+\\[]".toRegex().findAll(inputSubString).count() // matches '!+[]'
        if (digit == 0) {
            if ("\\+\\[]".toRegex().findAll(inputSubString).count() == 1) { // matches '+[]'
                return '0'
            }
        } else if (digit in 1..9) {
            return digit.digitToChar()
        }
        return '-' // Illegal digit
    }
}

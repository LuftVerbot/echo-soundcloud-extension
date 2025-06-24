package dev.brahmkshatriya.echo.extension

import java.util.Random

object RandomStringUtils {
    private val RANDOM = Random()

    fun randomAlphanumeric(
        count: Int
    ): String {
        var count = count
        var start = 0
        var end = 0
        if (count == 0) {
            return ""
        } else require(count >= 0) { "Requested random string length $count is less than 0." }

        end = 'z'.code + 1
        start = ' '.code

        val zero_digit_ascii = 48
        val first_letter_ascii = 65

        require(
            !(end <= zero_digit_ascii || end <= first_letter_ascii)
        ) {
            "Parameter end (" + end + ") must be greater then (" + zero_digit_ascii + ") for generating digits " +
                    "or greater then (" + first_letter_ascii + ") for generating letters."
        }

        val builder = StringBuilder(count)
        val gap = end - start

        while (count-- != 0) {
            val codePoint: Int = RANDOM.nextInt(gap) + start

            when (Character.getType(codePoint)) {
                Character.UNASSIGNED.toInt(), Character.PRIVATE_USE.toInt(), Character.SURROGATE.toInt() -> {
                    count++
                    continue
                }
            }

            val numberOfChars = Character.charCount(codePoint)
            if (count == 0 && numberOfChars > 1) {
                count++
                continue
            }

            if (Character.isLetter(codePoint) || Character.isDigit(codePoint)) {
                builder.appendCodePoint(codePoint)

                if (numberOfChars == 2) {
                    count--
                }
            } else {
                count++
            }
        }
        return builder.toString()
    }
}

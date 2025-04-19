package dev.brahmkshatriya.echo.extension

import java.util.Random

object RandomStringUtils {
    private val RANDOM = Random()

    fun randomAlphanumeric(count: Int): String {
        return random(count, true, true)
    }

    fun random(count: Int, letters: Boolean = false, numbers: Boolean = false): String {
        return random(count, 0, 0, letters, numbers)
    }

    private fun random(count: Int, start: Int, end: Int, letters: Boolean, numbers: Boolean): String {
        return random(count, start, end, letters, numbers, null, RANDOM)
    }

    private fun random(
        count: Int, start: Int, end: Int, letters: Boolean, numbers: Boolean,
        chars: CharArray?, random: Random
    ): String {
        var count = count
        var start = start
        var end = end
        if (count == 0) {
            return ""
        } else require(count >= 0) { "Requested random string length $count is less than 0." }
        require(!(chars != null && chars.size == 0)) { "The chars array must not be empty" }

        if (start == 0 && end == 0) {
            if (chars != null) {
                end = chars.size
            } else if (!letters && !numbers) {
                end = Character.MAX_CODE_POINT
            } else {
                end = 'z'.code + 1
                start = ' '.code
            }
        } else require(end > start) { "Parameter end ($end) must be greater than start ($start)" }

        val zero_digit_ascii = 48
        val first_letter_ascii = 65

        require(
            !(chars == null && (numbers && end <= zero_digit_ascii
                    || letters && end <= first_letter_ascii))
        ) {
            "Parameter end (" + end + ") must be greater then (" + zero_digit_ascii + ") for generating digits " +
                    "or greater then (" + first_letter_ascii + ") for generating letters."
        }

        val builder = StringBuilder(count)
        val gap = end - start

        while (count-- != 0) {
            val codePoint: Int
            if (chars == null) {
                codePoint = random.nextInt(gap) + start

                when (Character.getType(codePoint)) {
                    Character.UNASSIGNED.toInt(), Character.PRIVATE_USE.toInt(), Character.SURROGATE.toInt() -> {
                        count++
                        continue
                    }
                }
            } else {
                codePoint = chars[random.nextInt(gap) + start].code
            }

            val numberOfChars = Character.charCount(codePoint)
            if (count == 0 && numberOfChars > 1) {
                count++
                continue
            }

            if (letters && Character.isLetter(codePoint) || numbers && Character.isDigit(codePoint) || !letters && !numbers) {
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

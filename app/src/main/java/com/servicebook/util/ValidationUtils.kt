package com.servicebook.util

object ValidationUtils {

    /**
     * Filters input to Russian & Latin letters, spaces, and hyphens.
     */
    fun filterLettersOnly(input: String): String {
        return input.filter { it.isLetter() || it == ' ' || it == '-' }
    }

    /**
     * Filters input to digits only, with optional max length.
     */
    fun filterDigitsOnly(input: String, maxLength: Int? = null): String {
        val digits = input.filter { it.isDigit() }
        return if (maxLength != null && digits.length > maxLength) {
            digits.take(maxLength)
        } else {
            digits
        }
    }

    /**
     * Formats phone input into +7 (XXX) XXX-XX-XX mask.
     */
    fun formatPhone(rawInput: String): String {
        var digits = rawInput.filter { it.isDigit() }
        if (digits.startsWith("7") || digits.startsWith("8")) {
            digits = digits.drop(1)
        }
        if (digits.length > 10) {
            digits = digits.take(10)
        }

        if (digits.isEmpty()) {
            return "+7 "
        }

        val sb = StringBuilder("+7 (")
        for (i in digits.indices) {
            sb.append(digits[i])
            if (i == 2) sb.append(") ")
            else if (i == 5) sb.append("-")
            else if (i == 7) sb.append("-")
        }
        return sb.toString()
    }

    /**
     * Extracts pure 10 digits from a formatted phone string.
     */
    fun extractPhoneDigits(formattedPhone: String): String {
        var digits = formattedPhone.filter { it.isDigit() }
        if (digits.startsWith("7") || digits.startsWith("8")) {
            digits = digits.drop(1)
        }
        return digits.take(10)
    }

    /**
     * Validates Russian phone: must have exactly 10 digits after +7.
     */
    fun isValidPhone(phone: String): Boolean {
        return extractPhoneDigits(phone).length == 10
    }

    /**
     * Formats digits into DD.MM.YYYY mask.
     */
    fun formatDateInput(rawInput: String): String {
        val digits = rawInput.filter { it.isDigit() }.take(8)
        val sb = StringBuilder()
        for (i in digits.indices) {
            sb.append(digits[i])
            if (i == 1 || i == 3) {
                if (i < digits.length - 1) {
                    sb.append(".")
                }
            }
        }
        return sb.toString()
    }

    /**
     * Validates date DD.MM.YYYY:
     * - Day 01..31
     * - Month 01..12
     * - Year 1900..2035
     */
    fun isValidDate(dateString: String): Boolean {
        val parts = dateString.split(".")
        if (parts.size != 3) return false
        val day = parts[0].toIntOrNull() ?: return false
        val month = parts[1].toIntOrNull() ?: return false
        val year = parts[2].toIntOrNull() ?: return false

        if (month !in 1..12) return false
        if (day !in 1..31) return false
        if (year !in 1900..2035) return false

        if (month == 2 && day > 29) return false
        if (month in listOf(4, 6, 9, 11) && day > 30) return false

        return true
    }

    fun isValidDateDigits(rawDigits: String): Boolean {
        if (rawDigits.length != 8) return false
        val day = rawDigits.substring(0, 2).toIntOrNull() ?: return false
        val month = rawDigits.substring(2, 4).toIntOrNull() ?: return false
        val year = rawDigits.substring(4, 8).toIntOrNull() ?: return false
        if (month !in 1..12) return false
        if (day !in 1..31) return false
        if (year !in 1900..2035) return false
        if (month == 2 && day > 29) return false
        if (month in listOf(4, 6, 9, 11) && day > 30) return false
        return true
    }

    fun formatDigitsToDate(rawDigits: String): String {
        val d = rawDigits.filter { it.isDigit() }.take(8)
        if (d.length < 8) return d
        return "${d.substring(0, 2)}.${d.substring(2, 4)}.${d.substring(4, 8)}"
    }

    /**
     * Validates name / surname: length >= 2, only letters.
     */
    fun isValidName(name: String): Boolean {
        val trimmed = name.trim()
        return trimmed.length >= 2 && trimmed.all { it.isLetter() || it == ' ' || it == '-' }
    }

    /**
     * Parses and validates a car manufacturing year (1950..2030 inclusive).
     * Returns null if empty, non-numeric, or out of range.
     */
    fun parseValidCarYear(input: String): Int? {
        val trimmed = input.trim()
        if (trimmed.isEmpty() || !trimmed.all { it.isDigit() }) return null
        val year = trimmed.toIntOrNull() ?: return null
        return if (year in 1950..2030) year else null
    }

    fun isValidCarYear(input: String): Boolean {
        return parseValidCarYear(input) != null
    }

    /**
     * Formats the nearest maintenance due subtitle without duplicate prepositions.
     * Supports mileage-only, date-only, relative ("Через ..."), and combined deadlines.
     */
    fun formatMaintenanceDueSubtitle(
        targetMileage: Int,
        targetDate: String,
        formattedMileage: String
    ): String {
        val mileagePart = if (targetMileage > 0 && formattedMileage.isNotBlank()) {
            "На $formattedMileage км"
        } else {
            ""
        }

        val trimmedDate = targetDate.trim()
        val datePart = if (trimmedDate.isEmpty()) {
            ""
        } else {
            val lower = trimmedDate.lowercase()
            when {
                lower.startsWith("через ") -> {
                    if (mileagePart.isNotEmpty()) "через ${trimmedDate.drop(6)}" else trimmedDate
                }
                lower.startsWith("до ") -> {
                    if (mileagePart.isNotEmpty()) "до ${trimmedDate.drop(3)}" else trimmedDate
                }
                else -> {
                    if (mileagePart.isNotEmpty()) "до $trimmedDate" else "До $trimmedDate"
                }
            }
        }

        return when {
            mileagePart.isNotEmpty() && datePart.isNotEmpty() -> "$mileagePart · $datePart"
            mileagePart.isNotEmpty() -> mileagePart
            else -> datePart
        }
    }
}

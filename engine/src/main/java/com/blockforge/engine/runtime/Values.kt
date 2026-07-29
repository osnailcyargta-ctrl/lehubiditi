package com.blockforge.engine.runtime

/**
 * Block values are deliberately loose: a slot can hold "3", "3.0", "benar" or a reporter block, and
 * the runtime coerces on read. Coercion lives in one place so `"5" + 5` never disagrees between the
 * interpreter and the on-screen variable readouts.
 */
object Val {

    fun num(value: Any?): Double = when (value) {
        null -> 0.0
        is Double -> value
        is Float -> value.toDouble()
        is Int -> value.toDouble()
        is Long -> value.toDouble()
        is Boolean -> if (value) 1.0 else 0.0
        is String -> value.trim().replace(',', '.').toDoubleOrNull() ?: 0.0
        else -> 0.0
    }

    fun float(value: Any?): Float = num(value).toFloat()

    fun int(value: Any?): Int = num(value).toInt()

    fun str(value: Any?): String = when (value) {
        null -> ""
        is Double -> if (value == value.toLong().toDouble()) value.toLong().toString() else trimZeros(value)
        is Float -> str(value.toDouble())
        is Boolean -> if (value) "benar" else "salah"
        else -> value.toString()
    }

    fun bool(value: Any?): Boolean = when (value) {
        null -> false
        is Boolean -> value
        is Double -> value != 0.0
        is Float -> value != 0f
        is Int -> value != 0
        is String -> when (value.trim().lowercase()) {
            "", "0", "false", "salah", "tidak", "no" -> false
            else -> true
        }
        else -> true
    }

    /** True when both sides parse as numbers, so `"10" > "9"` compares numerically, not alphabetically. */
    fun compare(a: Any?, b: Any?): Int {
        val na = asNumberOrNull(a)
        val nb = asNumberOrNull(b)
        return if (na != null && nb != null) na.compareTo(nb)
        else str(a).lowercase().compareTo(str(b).lowercase())
    }

    private fun asNumberOrNull(value: Any?): Double? = when (value) {
        is Number -> value.toDouble()
        is Boolean -> if (value) 1.0 else 0.0
        is String -> value.trim().replace(',', '.').toDoubleOrNull()
        else -> null
    }

    private fun trimZeros(value: Double): String {
        val s = String.format(java.util.Locale.US, "%.4f", value).trimEnd('0').trimEnd('.')
        return if (s.isEmpty() || s == "-") "0" else s
    }

    /** Parses `#RRGGBB` / `#AARRGGBB` block colour slots, falling back to a visible colour. */
    fun color(value: Any?, fallback: Int = 0xFF4FC3F7.toInt()): Int {
        val s = str(value).trim()
        if (!s.startsWith("#")) return fallback
        return runCatching {
            val hex = s.substring(1)
            when (hex.length) {
                6 -> (0xFF000000.toInt()) or hex.toInt(16)
                8 -> hex.toLong(16).toInt()
                else -> fallback
            }
        }.getOrDefault(fallback)
    }
}

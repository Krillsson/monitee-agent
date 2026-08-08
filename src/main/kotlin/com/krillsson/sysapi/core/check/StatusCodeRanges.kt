package com.krillsson.sysapi.core.check

class StatusCodeRanges private constructor(private val ranges: List<IntRange>) {

    fun matches(code: Int) = ranges.any { code in it }

    companion object {
        private val VALID_CODES = 100..599

        fun parse(spec: String): StatusCodeRanges? {
            val parts = spec.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.isEmpty()) {
                return null
            }
            val ranges = parts.map { part ->
                val bounds = part.split('-').map { it.trim().toIntOrNull() }
                when {
                    bounds.size == 1 -> bounds[0]?.let { it..it }
                    bounds.size == 2 -> {
                        val from = bounds[0]
                        val to = bounds[1]
                        if (from != null && to != null && from <= to) from..to else null
                    }

                    else -> null
                } ?: return null
            }
            return if (ranges.all { it.first in VALID_CODES && it.last in VALID_CODES }) {
                StatusCodeRanges(ranges)
            } else {
                null
            }
        }
    }
}

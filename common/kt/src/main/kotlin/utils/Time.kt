package io.github.maxsh001.aphelion.utils

import kotlin.time.Duration
import kotlin.time.Instant

sealed class AphelionDuration {
    abstract fun isPositive(): Boolean
    abstract fun isNegative(): Boolean
    data class Time(val duration: Duration) : AphelionDuration() {
        override fun isPositive() = duration.isPositive()
        override fun isNegative() = duration.isNegative()
    }
    data class Tick(val tick: Int) : AphelionDuration() {
        override fun isPositive() = tick > 0
        override fun isNegative() = tick < 0
    }
}
sealed class AphelionInstant {
    data class Time(val instant: Instant) : AphelionInstant()
    data class Tick(val tick: Int) : AphelionInstant()
}

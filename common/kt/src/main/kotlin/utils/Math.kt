package io.github.maxsh001.aphelion.utils

import kotlinx.serialization.Serializable
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Serializable
data class Vec2(val x: Float, val y: Float) {
    companion object {
        const val EPSILON = 1e-5f
        const val EPSILON_SQUARED = EPSILON * EPSILON
        val ZERO = Vec2(0f, 0f)
        val UNIT_X = Vec2(1f, 0f)
        val UNIT_Y = Vec2(0f, 1f)
    }
    val length inline get() = sqrt(lengthSquared)
    val lengthSquared inline get() = x * x + y * y
    val angle inline get() = atan2(y, x)
    operator fun unaryPlus() = Vec2(x, y)
    operator fun unaryMinus() = Vec2(-x, -y)
    operator fun plus(addend: Vec2) = Vec2(x + addend.x, y + addend.y)
    operator fun minus(subtrahend: Vec2) = Vec2(x - subtrahend.x, y - subtrahend.y)
    operator fun times(factor: Float) = Vec2(x * factor, y * factor)
    operator fun div(divisor: Float) = Vec2(x / divisor, y / divisor)
    fun distance(vec2: Vec2) = (this - vec2).length
    fun distanceSquared(vec2: Vec2) = (this - vec2).lengthSquared
    fun normalize() = withLength(1f)
    fun withLength(length: Float) = this * (length / this.length)
    infix fun dot(vec2: Vec2) = this.x * vec2.x + this.y * vec2.y
    infix fun cross(vec2: Vec2) = this.x * vec2.y - this.y * vec2.x
    fun rotate(angle: Float): Vec2 {
        val cos = cos(angle)
        val sin = sin(angle)
        return Vec2(x * cos - y * sin, x * sin + y * cos)
    }
    fun project(vec2: Vec2): Vec2 {
        val lengthSquared = vec2.lengthSquared
        if (lengthSquared < EPSILON_SQUARED) return ZERO
        return vec2 * ((this dot vec2) / lengthSquared)
    }
}

operator fun Float.times(vec2: Vec2) = Vec2(vec2.x * this, vec2.y * this)

package io.github.maxsh001.aphelion.utils

import java.util.regex.Pattern
import kotlin.uuid.Uuid

interface Namespaced {
    val namespace: String
}

data class NamespacedKey(
    override val namespace: String,
    val key: String
) : Namespaced {
    companion object {
        const val SEPARATOR: String = ":"
        private val PATTERN = Pattern.compile("^(\\w+)$")
        private val NAMESPACED_PATTERN = Pattern.compile("^(?<namespace>\\w+)$SEPARATOR(?<key>\\w+)$")
        fun resolveOrNull(formatted: String): NamespacedKey? {
            val matcher = PATTERN.matcher(formatted)
            return if (matcher.matches())
                NamespacedKey(matcher.group("namespace"), matcher.group("key"))
            else null
        }
    }
    init {
        require(PATTERN.matcher(namespace).matches()) { "Invalid namespace: $namespace" }
        require(PATTERN.matcher(key).matches()) { "Invalid key: $key" }
    }
    constructor(namespaced: Namespaced, key: String) : this(namespaced.namespace, key)
}

interface Nameable {
    val name: String
}
interface UniquelyIdentifiable {
    val uniqueId: Uuid
}

enum class Priority {
    PRE,
    AFTER_PRE,
    FIRST,
    EARLY,
    DEFAULT,
    LATE,
    LAST,
    BEFORE_POST,
    POST,
}

typealias Color = String

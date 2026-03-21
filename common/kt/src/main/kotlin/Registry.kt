package io.github.maxsh001.aphelion

import io.github.maxsh001.aphelion.utils.NamespacedKey
import io.github.maxsh001.aphelion.utils.sync.readWriteLocked
import kotlin.reflect.KClass

interface RegistryHolder {
    val registries: Registries
}

class Registries {
    private val values = mutableSetOf<RegistryW<out Any>>().readWriteLocked()
    private class RegistryW<T : Any>(
        val type: KClass<T>,
        val registry: Registry<T>
    )
    inline suspend fun registry() {}
}

interface Registry<T> {
    suspend fun find(key: NamespacedKey): T?
    suspend operator fun get(key: NamespacedKey) = find(key)
}
interface MutableRegistry<T> : Registry<T> {
    suspend fun register(key: NamespacedKey, value: T): Boolean
    suspend operator fun set(key: NamespacedKey, value: T) = register(key, value)
}

internal class RegistryImpl<T> internal constructor(
    private val values: Map<NamespacedKey, T>
) : Registry<T> {
    override suspend  fun find(key: NamespacedKey) = values[key]
}
internal class MutatingRegistryImpl<T> : MutableRegistry<T> {
    private val values = mutableMapOf<NamespacedKey, T>().readWriteLocked()
    override suspend inline fun find(key: NamespacedKey) = values.read { get()[key] }
    override suspend fun register(key: NamespacedKey, value: T) = values.write {
        if (get().containsKey(key)) return@write false
        get()[key] = value
        true
    }
    suspend fun freeze() = RegistryImpl(values.read())
}

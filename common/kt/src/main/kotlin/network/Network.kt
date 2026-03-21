package io.github.maxsh001.aphelion.network

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.ClassDiscriminatorMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.overwriteWith
import kotlin.uuid.Uuid

@OptIn(ExperimentalSerializationApi::class)
val aphelionPacketSerialFormat = Json {
    classDiscriminator = "packetId"
    classDiscriminatorMode = ClassDiscriminatorMode.ALL_JSON_OBJECTS
    serializersModule = SerializersModule {}.overwriteWith(SerializersModule {
        contextual(Uuid::class, object : KSerializer<Uuid> {
            override val descriptor = PrimitiveSerialDescriptor("io.github.maxsh001.aphelion.network.UuidAsHexStringSerializer", PrimitiveKind.STRING)
            override fun serialize(encoder: Encoder, value: Uuid) = encoder.encodeString(value.toHexString())
            override fun deserialize(decoder: Decoder) = Uuid.parseHex(decoder.decodeString())
        })
    })
}

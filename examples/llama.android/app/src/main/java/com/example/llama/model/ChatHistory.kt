package com.example.llama.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.util.Date

@Serializable
data class ChatHistory(
    val id: String,
    val title: String,
    @Serializable(with = DateSerializer::class)
    val date: Date,
    val messages: List<String>
)

object DateSerializer : KSerializer<Date> {
    override val descriptor = PrimitiveSerialDescriptor("Date", PrimitiveKind.LONG)
    override fun serialize(encoder: Encoder, value: Date) {
        encoder.encodeLong(value.time)
    }
    override fun deserialize(decoder: Decoder): Date {
        return Date(decoder.decodeLong())
    }
}
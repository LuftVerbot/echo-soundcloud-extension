package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import dev.brahmkshatriya.echo.common.models.Shelf
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

class SoundCloudParser(private val session: SoundCloudSession) {

    fun JsonElement.toShelfItemsList(name: String? = "Unknown"): Shelf? {
        val itemsArray = jsonObject["items"]?.jsonArray ?: return null
        return null
    }

    fun JsonObject.toEchoMediaItem(): EchoMediaItem? {
        return null
    }
}
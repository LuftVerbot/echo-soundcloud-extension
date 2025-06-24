package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import dev.brahmkshatriya.echo.common.models.ImageHolder.Companion.toImageHolder
import dev.brahmkshatriya.echo.common.models.Playlist
import dev.brahmkshatriya.echo.common.models.Shelf
import dev.brahmkshatriya.echo.common.models.Track
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

class SoundCloudParser(private val session: SoundCloudSession) {

    fun JsonElement.toShelfItemsList(name: String = "Unknown"): Shelf? {
        val itemsArray = jsonObject["items"]?.jsonObject?.get("collection")?.jsonArray ?: return null
        val list = itemsArray.mapNotNull { it.jsonObject.toEchoMediaItem() }
        return if(list.isNotEmpty()) {
            Shelf.Lists.Items(
                title = name,
                list = list
            )
        } else {
            null
        }
    }

    fun JsonObject.toEchoMediaItem(): EchoMediaItem? {
        return jsonObject["kind"]?.jsonPrimitive?.content?.let { kind ->
            when {
                "track" in kind -> EchoMediaItem.TrackItem(toTrack())
                "system-playlist" in kind || "playlist" in kind -> EchoMediaItem.Lists.PlaylistItem(toPlaylist())
                else -> null
            }
        }
    }

    fun JsonObject.toPlaylist(): Playlist {
        return Playlist(
            id = jsonObject["id"]?.jsonPrimitive?.content.orEmpty(),
            title = jsonObject["title"]?.jsonPrimitive?.content.orEmpty(),
            cover = jsonObject["artwork_url"]?.jsonPrimitive?.content.orEmpty().replace("large", "t500x500").toImageHolder(),
            isEditable = true,
            description = jsonObject["description"]?.jsonPrimitive?.content.orEmpty(),
            tracks = jsonObject["track_count"]?.jsonPrimitive?.int
        )
    }

    fun JsonObject.toTrack(): Track {
        return Track(
            id = jsonObject["id"]?.jsonPrimitive?.content.orEmpty(),
            title = jsonObject["title"]?.jsonPrimitive?.content.orEmpty(),
            cover = jsonObject["artwork_url"]?.jsonPrimitive?.content.orEmpty().toImageHolder(),
            plays = jsonObject["playback_count"]?.jsonPrimitive?.long
        )
    }
}
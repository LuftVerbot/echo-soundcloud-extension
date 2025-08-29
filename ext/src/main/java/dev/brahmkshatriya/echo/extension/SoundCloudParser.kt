package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.models.Album
import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import dev.brahmkshatriya.echo.common.models.ImageHolder.Companion.toImageHolder
import dev.brahmkshatriya.echo.common.models.Playlist
import dev.brahmkshatriya.echo.common.models.Shelf
import dev.brahmkshatriya.echo.common.models.Streamable
import dev.brahmkshatriya.echo.common.models.Track
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

class SoundCloudParser(private val session: SoundCloudSession) {

    private val credentials: SoundCloudCredentials
        get() = session.credentials

    private val clientId: String
        get() = credentials.clientId

    fun JsonElement.toShelfItemsList(name: String = "Unknown"): Shelf? {
        val itemsArray = jsonObject["items"]?.jsonObject?.get("collection")?.jsonArray ?: jsonObject["collection"]?.jsonArray ?: return null
        val list = itemsArray.mapNotNull { it.jsonObject.toEchoMediaItem() }
        return if(list.isNotEmpty()) {
            Shelf.Lists.Items(
                id = name,
                title = name,
                list = list
            )
        } else {
            null
        }
    }

    fun JsonObject.toEchoMediaItem(): EchoMediaItem? {
        val kind = jsonObject["kind"]?.jsonPrimitive?.content.orEmpty()
        val setType = jsonObject["set_type"]?.jsonPrimitive?.content.orEmpty()
        return when {
            "track" in kind -> toTrack()
            "album" in setType -> toAlbum()
            "system-playlist" in kind || "playlist" in kind -> toPlaylist()
            else -> null
        }
    }

    fun JsonObject.toPlaylist(): Playlist {
        return Playlist(
            id = jsonObject["id"]?.jsonPrimitive?.content.orEmpty(),
            title = jsonObject["title"]?.jsonPrimitive?.content.orEmpty(),
            cover = jsonObject["artwork_url"]?.jsonPrimitive?.content?.replace("large", "t500x500")?.toImageHolder(),
            isEditable = true,
            description = jsonObject["description"]?.jsonPrimitive?.content,
            trackCount = jsonObject["track_count"]?.jsonPrimitive?.int?.toLong()
        )
    }

    fun JsonObject.toAlbum(): Album {
        return Album(
            id = jsonObject["id"]?.jsonPrimitive?.content.orEmpty(),
            title = jsonObject["title"]?.jsonPrimitive?.content.orEmpty(),
            cover = jsonObject["artwork_url"]?.jsonPrimitive?.content?.replace("large", "t500x500")?.toImageHolder(),
            description = jsonObject["description"]?.jsonPrimitive?.content,
            trackCount = jsonObject["track_count"]?.jsonPrimitive?.int?.toLong()
        )
    }

    fun JsonObject.toTrack(): Track {
        val mediaArray = jsonObject["media"]?.jsonObject?.get("transcodings")?.jsonArray
        val trackAuth = jsonObject["track_authorization"]?.jsonPrimitive?.content
        val mediaList = mediaArray?.mapNotNull {
            val url = it.jsonObject["url"]?.jsonPrimitive?.content
            Streamable(
                id = "$url?client_id=$clientId&track_authorization=$trackAuth",
                quality = 0,
                type = Streamable.MediaType.Server
            )
        } ?: emptyList()
        return Track(
            id = jsonObject["id"]?.jsonPrimitive?.content.orEmpty(),
            title = jsonObject["title"]?.jsonPrimitive?.content.orEmpty(),
            cover = jsonObject["artwork_url"]?.jsonPrimitive?.content?.toImageHolder(),
            plays = jsonObject["playback_count"]?.jsonPrimitive?.long,
            streamables = mediaList,
            extras = mapOf(
                "track_authorization" to jsonObject["track_authorization"]?.jsonPrimitive?.content.orEmpty()
            )
        )
    }
}
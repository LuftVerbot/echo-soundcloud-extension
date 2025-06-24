package dev.brahmkshatriya.echo.extension.clients

import dev.brahmkshatriya.echo.common.helpers.PagedData
import dev.brahmkshatriya.echo.common.models.Playlist
import dev.brahmkshatriya.echo.common.models.Shelf
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.extension.SoundCloudApi
import dev.brahmkshatriya.echo.extension.SoundCloudParser
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class SCPlaylistClient(private val api: SoundCloudApi, private val parser: SoundCloudParser) {

    fun getShelves(playlist: Playlist): PagedData.Single<Shelf> = PagedData.Single {
        emptyList()
    }

    suspend fun loadPlaylist(playlist: Playlist): Playlist {
        val jsonObject = api.getPlaylist(playlist.id)
        return parser.run { jsonObject.toPlaylist() }
    }

    fun loadTracks(playlist: Playlist): PagedData<Track> = PagedData.Single {
        val jsonObject = api.getPlaylist(playlist.id)
        val tracksPlaylistArray = jsonObject["tracks"]?.jsonArray ?: JsonArray(emptyList())
        val trackIds = tracksPlaylistArray.map { track ->
            track.jsonObject["id"]?.jsonPrimitive?.content.orEmpty()
        }
        val trackArray = api.getTracks(trackIds)
        trackArray.map { track ->
            parser.run { track.jsonObject.toTrack() }
        }
    }
}
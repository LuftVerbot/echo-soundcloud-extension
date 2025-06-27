package dev.brahmkshatriya.echo.extension.clients

import dev.brahmkshatriya.echo.common.helpers.ContinuationCallback.Companion.await
import dev.brahmkshatriya.echo.common.helpers.PagedData
import dev.brahmkshatriya.echo.common.models.Request.Companion.toRequest
import dev.brahmkshatriya.echo.common.models.Shelf
import dev.brahmkshatriya.echo.common.models.Streamable
import dev.brahmkshatriya.echo.common.models.Streamable.Media.Companion.toMedia
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.extension.SoundCloudApi
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request

class SCTrackClient(private val api: SoundCloudApi) {

    fun getShelves(track: Track): PagedData<Shelf> {
        TODO("Not yet implemented")
    }

    suspend fun loadStreamableMedia(
        streamable: Streamable,
        isDownload: Boolean
    ): Streamable.Media {
        val request = Request.Builder().url(streamable.id).addHeader("Authorization", "OAuth ${api.accessToken}").build()
        val response = api.client.newCall(request).await()
        val body = response.body.string()
        val jsonObject = api.decodeJson(body)
        val url = jsonObject["url"]?.jsonPrimitive?.content.orEmpty()
        println("FUCK YOU $url")
        return Streamable.Source.Http(
            request = url.toRequest(),
            quality = streamable.quality,
            type = Streamable.SourceType.HLS
        ).toMedia()
    }

    suspend fun loadTrack(track: Track): Track {
        return track
    }
}
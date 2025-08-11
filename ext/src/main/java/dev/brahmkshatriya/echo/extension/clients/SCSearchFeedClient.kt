package dev.brahmkshatriya.echo.extension.clients

import dev.brahmkshatriya.echo.common.helpers.PagedData
import dev.brahmkshatriya.echo.common.models.Feed
import dev.brahmkshatriya.echo.common.models.Feed.Companion.toFeed
import dev.brahmkshatriya.echo.common.models.Shelf
import dev.brahmkshatriya.echo.extension.SoundCloudApi

class SCSearchFeedClient(private val api: SoundCloudApi) {

    suspend fun loadSearchFeed(query: String): Feed<Shelf> = PagedData.Single<Shelf> {
        val searchObject = api.search(query, "tracks")
        emptyList()
    }.toFeed()
}
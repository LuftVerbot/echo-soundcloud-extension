package dev.brahmkshatriya.echo.extension.clients

import dev.brahmkshatriya.echo.common.helpers.PagedData
import dev.brahmkshatriya.echo.common.models.Feed
import dev.brahmkshatriya.echo.common.models.Feed.Companion.toFeed
import dev.brahmkshatriya.echo.common.models.Shelf
import dev.brahmkshatriya.echo.extension.SoundCloudApi
import dev.brahmkshatriya.echo.extension.SoundCloudParser
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class SCHomeFeedClient(private val api: SoundCloudApi, private val parser: SoundCloudParser) {

    fun getHomeFeed(): Feed = PagedData.Single {
        val homeCollection = api.homePage()["collection"]?.jsonArray ?: JsonArray(emptyList())

        homeCollection.mapNotNull { selection ->
            parser.run {
                selection.toShelfItemsList(selection.jsonObject["title"]?.jsonPrimitive?.content.orEmpty())
            }
        }
    }.toFeed()
}
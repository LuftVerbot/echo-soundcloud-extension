package dev.brahmkshatriya.echo.extension.clients

import dev.brahmkshatriya.echo.common.helpers.PagedData
import dev.brahmkshatriya.echo.common.models.Shelf
import dev.brahmkshatriya.echo.extension.SoundCloudApi
import dev.brahmkshatriya.echo.extension.SoundCloudParser
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class SDHomeFeedClient(private val api: SoundCloudApi, private val parser: SoundCloudParser) {

    fun getHomeFeed(): PagedData<Shelf> = PagedData.Single {
        val homeCollection = api.homePage()["collection"]?.jsonArray ?: JsonArray(emptyList())

        homeCollection.mapNotNull { section ->
            parser.run {
                section.toShelfItemsList(section.jsonObject["title"]?.jsonPrimitive?.content)
            }
        }
        emptyList()
    }
}
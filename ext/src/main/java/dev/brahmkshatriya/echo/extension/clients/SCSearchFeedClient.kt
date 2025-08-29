package dev.brahmkshatriya.echo.extension.clients

import dev.brahmkshatriya.echo.common.models.Feed
import dev.brahmkshatriya.echo.common.models.Feed.Companion.toFeedData
import dev.brahmkshatriya.echo.common.models.Shelf
import dev.brahmkshatriya.echo.common.models.Tab
import dev.brahmkshatriya.echo.extension.SoundCloudApi
import dev.brahmkshatriya.echo.extension.SoundCloudParser
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

class SCSearchFeedClient(private val api: SoundCloudApi, private val parser: SoundCloudParser) {

    @Volatile
    private var oldSearch: Pair<String, List<Shelf>>? = null

    suspend fun loadSearchFeed(query: String): Feed<Shelf>  {
        return Feed(loadSearchFeedTabs(query)) { tab ->
            if (tab?.id == "All") {
                oldSearch?.takeIf { it.first == query }?.second.let {
                    return@Feed it?.toFeedData()!!
                }
            }

            val searchObject = when(tab!!.id) {
                in "Tracks" -> api.search(query, "tracks")
                in "Albums" -> api.search(query, "albums")
                in "Playlists" -> api.search(query, "playlists_without_albums")
                else -> null
            }

            val processSearchResults: (JsonObject) -> List<Shelf> = { resultObj ->
                val dataArray = resultObj["collection"]?.jsonArray

                dataArray?.mapNotNull { item ->
                    parser.run {
                        item.jsonObject.toEchoMediaItem()?.toShelf()
                    }
                } ?: emptyList()
            }

            return@Feed processSearchResults(searchObject ?: JsonObject(emptyMap())).toFeedData()
        }
    }

    suspend fun loadSearchFeedTabs(query: String): List<Tab> {
        query.ifBlank { return emptyList() }

        oldSearch = query to searchTabs.mapNotNull { tab ->
            val searchObject = when(tab.id) {
                in "Tracks" -> api.search(query, "tracks")
                in "Albums" -> api.search(query, "albums")
                in "Playlists" -> api.search(query, "playlists_without_albums")
                else -> null
            }
            parser.run {
                searchObject?.toShelfItemsList(tab.title)
            }
        }
        return listOf(Tab("All", "All")) + searchTabs
    }

    companion object {
        private val searchTabs = listOf(
            Tab("Tracks", "Tracks"),
            Tab("Albums", "Albums"),
            Tab("Playlists", "Playlists")
        )
    }
}
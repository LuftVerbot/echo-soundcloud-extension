package dev.brahmkshatriya.echo.extension.clients

import dev.brahmkshatriya.echo.common.helpers.PagedData
import dev.brahmkshatriya.echo.common.models.Shelf

class SDHomeFeedClient {

    fun getHomeFeed(): PagedData<Shelf> = PagedData.Single {
        emptyList()
    }
}
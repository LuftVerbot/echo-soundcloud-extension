package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.clients.ExtensionClient
import dev.brahmkshatriya.echo.common.clients.HomeFeedClient
import dev.brahmkshatriya.echo.common.clients.LoginClient
import dev.brahmkshatriya.echo.common.helpers.PagedData
import dev.brahmkshatriya.echo.common.models.Request
import dev.brahmkshatriya.echo.common.models.Request.Companion.toRequest
import dev.brahmkshatriya.echo.common.models.Shelf
import dev.brahmkshatriya.echo.common.models.Tab
import dev.brahmkshatriya.echo.common.models.User
import dev.brahmkshatriya.echo.common.settings.Setting
import dev.brahmkshatriya.echo.common.settings.Settings
import dev.brahmkshatriya.echo.extension.clients.SDHomeFeedClient

class SoundCloudExtension : HomeFeedClient, ExtensionClient, LoginClient.WebView.Cookie {

    override suspend fun onExtensionSelected() {}

    override val settingItems: List<Setting> = emptyList()

    private lateinit var setting: Settings

    override fun setSettings(settings: Settings) {
        setting = settings
    }

    //<============= HomeTab =============>

    private val sdHomeFeedClient = SDHomeFeedClient()

    override suspend fun getHomeTabs(): List<Tab> = listOf()

    override fun getHomeFeed(tab: Tab?): PagedData<Shelf> = sdHomeFeedClient.getHomeFeed()

    //<============= Login =============>

    override suspend fun getCurrentUser(): User? {
        TODO("Not yet implemented")
    }

    override val loginWebViewInitialUrl = "https://soundcloud.com/signin".toRequest()

    override val loginWebViewStopUrlRegex = "https://secure\\.soundcloud\\.com/oauth/token.*".toRegex()

    override suspend fun onLoginWebviewStop(url: String, data: String): List<User> {
        println("FUCK YOU $data")
        return emptyList()
    }

    override suspend fun onSetLoginUser(user: User?) {
        TODO("Not yet implemented")
    }
}
package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.clients.ExtensionClient
import dev.brahmkshatriya.echo.common.clients.HomeFeedClient
import dev.brahmkshatriya.echo.common.clients.LoginClient
import dev.brahmkshatriya.echo.common.clients.PlaylistClient
import dev.brahmkshatriya.echo.common.clients.QuickSearchClient
import dev.brahmkshatriya.echo.common.clients.SearchFeedClient
import dev.brahmkshatriya.echo.common.clients.TrackClient
import dev.brahmkshatriya.echo.common.helpers.ContinuationCallback.Companion.await
import dev.brahmkshatriya.echo.common.helpers.WebViewRequest
import dev.brahmkshatriya.echo.common.models.Feed
import dev.brahmkshatriya.echo.common.models.NetworkRequest
import dev.brahmkshatriya.echo.common.models.NetworkRequest.Companion.toGetRequest
import dev.brahmkshatriya.echo.common.models.Playlist
import dev.brahmkshatriya.echo.common.models.QuickSearchItem
import dev.brahmkshatriya.echo.common.models.Shelf
import dev.brahmkshatriya.echo.common.models.Streamable
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.common.models.User
import dev.brahmkshatriya.echo.common.settings.Setting
import dev.brahmkshatriya.echo.common.settings.Settings
import dev.brahmkshatriya.echo.extension.SoundCloudApi.Companion.client
import dev.brahmkshatriya.echo.extension.clients.SCHomeFeedClient
import dev.brahmkshatriya.echo.extension.clients.SCPlaylistClient
import dev.brahmkshatriya.echo.extension.clients.SCSearchFeedClient
import dev.brahmkshatriya.echo.extension.clients.SCTrackClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import kotlin.io.encoding.Base64

class SoundCloudExtension : HomeFeedClient, PlaylistClient, TrackClient, SearchFeedClient,
    QuickSearchClient, ExtensionClient, LoginClient.WebView {

    private val session by lazy { SoundCloudSession.getInstance() }
    private val api by lazy { SoundCloudApi(session) }
    private val parser by lazy { SoundCloudParser(session) }

    override suspend fun getSettingItems(): List<Setting> = emptyList()

    override fun setSettings(settings: Settings) {
        session.settings = settings
    }

    override suspend fun onExtensionSelected() {
        session.settings?.let { setSettings(it) }
    }

    //<============= HomeTab =============>

    private val scHomeFeedClient by lazy { SCHomeFeedClient(api, parser) }

    override suspend fun loadHomeFeed(): Feed<Shelf> = scHomeFeedClient.loadHomeFeed()

    //<============= Playlist =============>

    private val scPlaylistClient by lazy { SCPlaylistClient(api, parser) }

    override suspend fun loadPlaylist(playlist: Playlist): Playlist = scPlaylistClient.loadPlaylist(playlist)

    override suspend fun loadTracks(playlist: Playlist): Feed<Track> = scPlaylistClient.loadTracks(playlist)

    override suspend fun loadFeed(playlist: Playlist): Feed<Shelf>? = scPlaylistClient.loadFeed()

    //<============= Track =============>

    private val scTrackClient by lazy { SCTrackClient(api) }

    override suspend fun loadFeed(track: Track): Feed<Shelf> = scTrackClient.loadFeed(track)

    override suspend fun loadStreamableMedia(
        streamable: Streamable,
        isDownload: Boolean
    ): Streamable.Media = scTrackClient.loadStreamableMedia(streamable, isDownload)

    override suspend fun loadTrack(track: Track, isDownload: Boolean): Track = scTrackClient.loadTrack(track)

    //<============= Search =============>

    private val scSearchFeedClient by lazy { SCSearchFeedClient(api, parser) }

    override suspend fun loadSearchFeed(query: String): Feed<Shelf> = scSearchFeedClient.loadSearchFeed(query)

    override suspend fun quickSearch(query: String): List<QuickSearchItem> {
        val queryObj = api.search(query, "queries")
        val collArray = queryObj["collection"]?.jsonArray
        return collArray?.mapNotNull {
            QuickSearchItem.Query(
               it.jsonObject["query"]?.jsonPrimitive?.content.orEmpty(),
                false
            )
        }?.toList() ?: emptyList()
    }

    override suspend fun deleteQuickSearch(item: QuickSearchItem) {
        TODO("Not yet implemented")
    }

    //<============= Login =============>

    override suspend fun getCurrentUser(): User {
        val user = api.makeUser()
        return user
    }

    override val webViewRequest = object : WebViewRequest.Headers<List<User>> {
        override suspend fun onStop(requests: List<NetworkRequest>): List<User> {
            var cookie = ""
            requests.forEach {
                if (it.url.contains("oauth/authorize")) {
                    if(it.headers["cookie"]?.contains("_soundcloud_session") == true) {
                        cookie = it.headers["cookie"].orEmpty()
                    }
                }
            }

            val clientId = getClientID(cookie)

            fun b64url(b: ByteArray) = Base64.UrlSafe
                .withPadding(Base64.PaddingOption.ABSENT)
                .encode(b, 0, b.size)
            fun pkce(): Pair<String, String> {
                val rnd = ByteArray(32).also { SecureRandom().nextBytes(it) }
                val verifier = b64url(rnd)
                val challenge = b64url(
                    MessageDigest.getInstance("SHA-256")
                        .digest(verifier.toByteArray(Charsets.UTF_8))
                )
                return verifier to challenge
            }

            val (codeVerifier, codeChallenge) = pkce()
            val state =
                b64url("""{"client_id":"$clientId","nonce":"${UUID.randomUUID()}"}""".toByteArray())

            val authBody = """
            {"client_id":"$clientId","redirect_uri":"https://soundcloud.com/signin/callback",
             "response_type":"code","code_challenge":"$codeChallenge","code_challenge_method":"S256",
             "state":"$state"}
            """.trimIndent()

            val authReq = Request.Builder()
                .url("https://api-auth.soundcloud.com/oauth/authorize?client_id=$clientId")
                .post(authBody.toRequestBody("application/json; charset=UTF-8".toMediaType()))
                .addHeader("Cookie", cookie)
                .build()

            val authJson = client.newCall(authReq).await().use { r ->
                check(r.isSuccessful) { "Auth failed: $r" }; r.body.string()
            }

            val redirectUri = Json.parseToJsonElement(authJson).jsonObject["redirect_uri"]
                ?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                ?: error("No redirect_uri in auth response")
            val code = redirectUri.toHttpUrlOrNull()?.queryParameter("code")
                ?: error("No 'code' in redirect_uri")

            val tokenForm = FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("client_id", clientId)
                .add("code", code)
                .add("code_verifier", codeVerifier)
                .add("redirect_uri", "https://soundcloud.com/signin/callback")
                .build()
            val tokenReq = Request.Builder()
                .url("https://secure.soundcloud.com/oauth/token?grant_type=authorization_code&client_id=$clientId")
                .post(tokenForm)
                .addHeader(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
                )
                .addHeader("Accept", "application/json")
                .build()

            val tokenJson = client.newCall(tokenReq).await().use { r ->
                check(r.isSuccessful) { "Token exchange failed: $r" }; r.body.string()
            }
            val accessToken = Json.parseToJsonElement(tokenJson).jsonObject["access_token"]
                ?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                ?: error("No access_token in token response")

            session.updateCredentials(accessToken = accessToken, clientId = clientId)

            return listOf(api.makeUser(accessToken))
        }

        override val initialUrl = "https://m.soundcloud.com/signin".toGetRequest(
            mapOf("User-Agent" to "Mozilla/5.0 (Linux; Android 14; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/113.0.5672.136 Mobile Safari/537.36")
        )
        override val interceptUrlRegex = "https://api-auth\\.soundcloud\\.com/oauth/authorize\\?.*".toRegex()
        override val stopUrlRegex = "https://m\\.soundcloud\\.com/signin/callback.*".toRegex()
    }

    private suspend fun getClientID(cookie: String): String = withContext(Dispatchers.IO) {
        val html = client.newCall(
            Request.Builder()
                .url("https://soundcloud.com/")
                .addHeader("Cookie", cookie)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36")
                .build()
        ).await().body.string()

        val scriptSrc = Regex("<script[^>]+src=\"([^\"]+?sndcdn.com[^\"]+)\"")
            .findAll(html).map { it.groupValues[1] }

        scriptSrc.forEach { jsUrl ->
            val js = client.newCall(Request.Builder().url(jsUrl).build()).await().body.string()
            Regex("client_id\\s*:\\s*\"(\\w{32})\"").find(js)?.groupValues?.get(1)?.let { return@withContext it }
        }
        error("Client ID could not be retrieved")
    }

    override fun setLoginUser(user: User?) {
        if (user != null) {
            session.updateCredentials(
                accessToken = user.extras["accessToken"] ?: "",
                clientId = user.extras["userId"] ?: "",
                userId = user.extras["clientId"] ?: ""
            )
        } else {
            session.updateCredentials(
                accessToken = "",
                clientId = "",
                userId = ""
            )
        }
    }

    //<============= Utils =============>

    fun handleArlExpiration() {

    }
}
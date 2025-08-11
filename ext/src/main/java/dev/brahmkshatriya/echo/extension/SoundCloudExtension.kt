package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.clients.ExtensionClient
import dev.brahmkshatriya.echo.common.clients.HomeFeedClient
import dev.brahmkshatriya.echo.common.clients.LoginClient
import dev.brahmkshatriya.echo.common.clients.PlaylistClient
import dev.brahmkshatriya.echo.common.clients.SearchFeedClient
import dev.brahmkshatriya.echo.common.clients.TrackClient
import dev.brahmkshatriya.echo.common.helpers.ContinuationCallback.Companion.await
import dev.brahmkshatriya.echo.common.helpers.WebViewRequest
import dev.brahmkshatriya.echo.common.models.Feed
import dev.brahmkshatriya.echo.common.models.NetworkRequest
import dev.brahmkshatriya.echo.common.models.NetworkRequest.Companion.toGetRequest
import dev.brahmkshatriya.echo.common.models.Playlist
import dev.brahmkshatriya.echo.common.models.Shelf
import dev.brahmkshatriya.echo.common.models.Streamable
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.common.models.User
import dev.brahmkshatriya.echo.common.settings.Setting
import dev.brahmkshatriya.echo.common.settings.Settings
import dev.brahmkshatriya.echo.extension.clients.SCHomeFeedClient
import dev.brahmkshatriya.echo.extension.clients.SCPlaylistClient
import dev.brahmkshatriya.echo.extension.clients.SCSearchFeedClient
import dev.brahmkshatriya.echo.extension.clients.SCTrackClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class SoundCloudExtension : HomeFeedClient, PlaylistClient, TrackClient, SearchFeedClient,
    ExtensionClient, LoginClient.WebView {

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

    private val scSearchFeedClient by lazy { SCSearchFeedClient(api) }

    override suspend fun loadSearchFeed(query: String): Feed<Shelf> = scSearchFeedClient.loadSearchFeed(query)

    //<============= Login =============>

    private val client by lazy { OkHttpClient() }

    override suspend fun getCurrentUser(): User {
        val user = api.makeUser()
        return user
    }

    @OptIn(ExperimentalEncodingApi::class)
    override val webViewRequest = object : WebViewRequest.Headers<List<User>> {
        override suspend fun onStop(requests: List<NetworkRequest>): List<User> {
            val loginUserAgent =
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

            var cookie = ""
            requests.forEach {
                if (it.url.contains("oauth/authorize")) {
                    if(it.headers["Cookie"]?.contains("_soundcloud_session") == true) {
                        cookie = it.headers["Cookie"].orEmpty()
                    }
                }
            }

            val clientId = getClientID(cookie)

            val codeVerifier = RandomStringUtils.randomAlphanumeric(64)
            val codeChallenge = MessageDigest
                .getInstance("SHA-256")
                .digest(codeVerifier.toByteArray(Charsets.UTF_8))
                .let {
                    Base64.UrlSafe.encode(it, 0, it.size)
                }.trimEnd('=')

            val nonce = RandomStringUtils.randomAlphanumeric(64)
            val stateJson = """{"client_id":"$clientId","nonce":"$nonce"}"""
            val bytes = stateJson.toByteArray(Charsets.UTF_8)
            val rawState = Base64.UrlSafe.encode(bytes, 0, bytes.size)
            val state = rawState.trimEnd('=')

            val cookieJar = object : CookieJar {
                private val store = mutableMapOf<String, List<Cookie>>()
                override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                    store[url.host] = cookies
                }

                override fun loadForRequest(url: HttpUrl) = store[url.host] ?: emptyList()
            }

            "https://soundcloud.com"
                .toHttpUrl()
                .let { baseUrl ->
                    cookie.split(';')
                        .mapNotNull { Cookie.parse(baseUrl, it.trim()) }
                        .takeIf { it.isNotEmpty() }
                        ?.let { cookieJar.saveFromResponse(baseUrl, it) }
                }

            val clientWithJar = client.newBuilder()
                .cookieJar(cookieJar)
                .build()

            val authBody = """
      {
        "client_id":"$clientId",
        "redirect_uri":"https://soundcloud.com/signin/callback",
        "response_type":"code",
        "code_challenge":"$codeChallenge",
        "code_challenge_method":"S256",
        "state":"$state"
      }
    """.trimIndent()
            val authReq = Request.Builder()
                .url("https://api-auth.soundcloud.com/oauth/authorize?client_id=$clientId")
                .post(authBody.toRequestBody("application/json; charset=UTF-8".toMediaType()))
                .addHeader("Cookie", cookie)
                .build()

            val authResponseJson = clientWithJar.newCall(authReq).await().use { resp ->
                if (!resp.isSuccessful) throw IllegalStateException("Auth failed: $resp")
                resp.body.string()
            }

            val redirectUri = Json.parseToJsonElement(authResponseJson)
                .jsonObject["redirect_uri"]
                ?.jsonPrimitive
                ?.content
                .takeIf { it!!.isNotBlank() }
                ?: throw IllegalStateException("No redirect_uri in auth response")
            val code = redirectUri.toHttpUrlOrNull()
                ?.queryParameter("code")
                ?: throw IllegalStateException("No code in redirect_uri: $redirectUri")

            val tokenForm = FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("client_id", clientId)
                .add("code", code)
                .add("code_verifier", codeVerifier)
                .add("redirect_uri", "https://soundcloud.com/signin/callback")
                .build()
            val tokenUrl = "https://secure.soundcloud.com/oauth/token" +
                    "?grant_type=authorization_code&client_id=$clientId"
            val tokenReq = Request.Builder()
                .url(tokenUrl)
                .post(tokenForm)
                .addHeader("User-Agent", loginUserAgent)
                .addHeader("Accept", "application/json")
                .build()

            val tokenResponseJson = withContext(Dispatchers.IO) {
                clientWithJar.newCall(tokenReq).execute().use { resp ->
                    if (!resp.isSuccessful) throw IllegalStateException("Token exchange failed: $resp")
                    resp.body.string()
                }
            }
            val accessToken = Json.parseToJsonElement(tokenResponseJson)
                .jsonObject["access_token"]
                ?.jsonPrimitive
                ?.content
                .takeIf { it!!.isNotBlank() }
                ?: throw IllegalStateException("No access_token in token response")
            session.updateCredentials(accessToken = accessToken, clientId = clientId)

            println("FUCK YOU $accessToken")

            return listOf(api.makeUser(accessToken))
        }

        override val initialUrl = "https://m.soundcloud.com/signin".toGetRequest(
            mapOf(
                Pair(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14; sdk_gphone64_x86_64 Build/UE1A.230829.050; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/113.0.5672.136 Mobile Safari/537.36"
                )
            )
        )
        override val interceptUrlRegex = "https://api-auth\\.soundcloud\\.com/oauth/authorize\\?.*".toRegex()

        override val stopUrlRegex = "https://m\\.soundcloud\\.com/signin/callback.*".toRegex()
    }

    private suspend fun getClientID(data: String): String = withContext(Dispatchers.IO) {
        val mainRequest = Request.Builder().url("https://soundcloud.com/").addHeader("Cookie", data).addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36").build()
        val mainResponse = client.newCall(mainRequest).await()
        val mainHtml = mainResponse.body.string()

        val scriptRegex = Regex("<script[^\">]+?src=\"([^\"]+?sndcdn.com[^\"]+?)\"")

        for (match in scriptRegex.findAll(mainHtml)) {
            val scriptUrl = match.groupValues[1]

            val request = Request.Builder().url(scriptUrl)
                .addHeader(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
                )
                .build()

            val result2 = client.newCall(request).await().body.string()

            if (result2.isEmpty()) {
                println("Client id not found in '$scriptUrl'")
            } else {
                val clientIdRegex = Regex("client_id\\s*?:\\s*?\"(\\w{32})\"")
                val clientIdMatch = clientIdRegex.find(result2)
                if (clientIdMatch == null) {
                    println("Client id not found in '$scriptUrl'")
                } else {
                    val clientId = clientIdMatch.groupValues[1]
                    println("Client id refreshed from '$scriptUrl': $clientId")
                    return@withContext clientId
                }
            }
        }
        throw IllegalStateException("Client ID could not be retrieved")
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
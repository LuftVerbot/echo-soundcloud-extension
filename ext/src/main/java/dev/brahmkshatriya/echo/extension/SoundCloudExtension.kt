package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.clients.ExtensionClient
import dev.brahmkshatriya.echo.common.clients.HomeFeedClient
import dev.brahmkshatriya.echo.common.clients.LoginClient
import dev.brahmkshatriya.echo.common.helpers.PagedData
import dev.brahmkshatriya.echo.common.models.ImageHolder.Companion.toImageHolder
import dev.brahmkshatriya.echo.common.models.Request.Companion.toRequest
import dev.brahmkshatriya.echo.common.models.Shelf
import dev.brahmkshatriya.echo.common.models.Tab
import dev.brahmkshatriya.echo.common.models.User
import dev.brahmkshatriya.echo.common.settings.Setting
import dev.brahmkshatriya.echo.common.settings.Settings
import dev.brahmkshatriya.echo.extension.clients.SDHomeFeedClient
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

class SoundCloudExtension : HomeFeedClient, ExtensionClient, LoginClient.WebView.Cookie {

    private val session = SoundCloudSession.getInstance()
    private val api = SoundCloudApi(session)

    override val settingItems: List<Setting> = emptyList()

    override fun setSettings(settings: Settings) {
        session.settings = settings
    }

    override suspend fun onExtensionSelected() {
        session.settings?.let { setSettings(it) }
    }

    //<============= HomeTab =============>

    private val sdHomeFeedClient = SDHomeFeedClient()

    override suspend fun getHomeTabs(): List<Tab> = listOf()

    override fun getHomeFeed(tab: Tab?): PagedData<Shelf> = sdHomeFeedClient.getHomeFeed()

    //<============= Login =============>

    private val client = OkHttpClient()

    override suspend fun getCurrentUser(): User? {
        TODO("Not yet implemented")
    }

    override val loginWebViewInitialUrl =
        "https://m.soundcloud.com/signin".toRequest(
            mapOf(
                Pair(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14; sdk_gphone64_x86_64 Build/UE1A.230829.050; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/113.0.5672.136 Mobile Safari/537.36"
                )
            )
        )

    override val loginWebViewStopUrlRegex = "https://m\\.soundcloud\\.com/signin/callback.*".toRegex()
    /*override val loginWebViewStopUrlRegex = Regex("""^https://m\.soundcloud\.com/signin/callback.*""")*/

    override val loginWebViewCookieUrlRegex = "https://api-auth\\.soundcloud\\.com/oauth/authorize\\?.*".toRegex()
    /*override val loginWebViewCookieUrlRegex = Regex("""^https://api\-auth\.soundcloud\.com/oauth/authorize(?:\?.*)?$""")*/

    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun onLoginWebviewStop(url: String, data: Map<String, String>): List<User> {
        val loginUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"


        val fData = data.values
            .joinToString().split(",").map { it.trim() }.distinct().joinToString(";")

        val clientId = getClientID(fData)

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
        val rawState = Base64.UrlSafe.encode(bytes,0, bytes.size)
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
                fData.split(';')
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
            .addHeader("Cookie", fData)
            .build()

        val authResponseJson = clientWithJar.newCall(authReq).execute().use { resp ->
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

        val tokenResponseJson = clientWithJar.newCall(tokenReq).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("Token exchange failed: $resp")
            resp.body.string()
        }
        val accessToken = Json.parseToJsonElement(tokenResponseJson)
            .jsonObject["access_token"]
            ?.jsonPrimitive
            ?.content
            .takeIf { it!!.isNotBlank() }
            ?: throw IllegalStateException("No access_token in token response")

        return listOf(api.makeUser(accessToken))
    }

    private fun getClientID(data: String): String {
        val client = OkHttpClient()

        val mainRequest = Request.Builder().url("https://soundcloud.com/").addHeader("Cookie", data).addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36").build()
        val mainResponse = client.newCall(mainRequest).execute()
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

            val result2 = client.newCall(request).execute().body.string()

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
                    return clientId
                }
            }
        }
        throw IllegalStateException("Client ID could not be retrieved")
    }

    /*https://api-v2.soundcloud.com/users/344645387?client_id=EjkRJG0BLNEZquRiPZYdNtJdyGtTuHdp&app_version=1744919743&app_locale=de*/

//    GET /users/344645387?client_id=EjkRJG0BLNEZquRiPZYdNtJdyGtTuHdp&app_version=1744919743&app_locale=de HTTP/1.1
//    Host: api-v2.soundcloud.com
//    User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:137.0) Gecko/20100101 Firefox/137.0
//    Accept: application/json, text/javascript, */*; q=0.01
//Accept-Language: de,en-US;q=0.7,en;q=0.3
//Accept-Encoding: gzip, deflate, br, zstd
//Referer: https://soundcloud.com/
//Origin: https://soundcloud.com
//DNT: 1
//Sec-GPC: 1
//Sec-Fetch-Dest: empty
//Sec-Fetch-Mode: cors
//Sec-Fetch-Site: same-site
//Authorization: OAuth 2-302405-344645387-RbSKNDlFabNnMg
//Connection: keep-alive
//Pragma: no-cache
//Cache-Control: no-cache

    override suspend fun onSetLoginUser(user: User?) {}
}
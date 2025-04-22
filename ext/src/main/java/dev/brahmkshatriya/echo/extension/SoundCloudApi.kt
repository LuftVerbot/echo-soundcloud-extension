package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.helpers.ContinuationCallback.Companion.await
import dev.brahmkshatriya.echo.common.models.ImageHolder.Companion.toImageHolder
import dev.brahmkshatriya.echo.common.models.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import java.util.zip.GZIPInputStream

class SoundCloudApi(private val session: SoundCloudSession) {

    init {
        if(session.credentials == null) {
            session.credentials = SoundCloudCredentials(
                accessToken = "",
                clientId = "",
                userId = ""
            )
        }
    }

    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
    }

    private val credentials: SoundCloudCredentials
        get() = session.credentials ?: throw IllegalStateException("SoundCloudCredentials not initialized")

    private val accessToken: String
        get() = credentials.accessToken

    private val clientId: String
        get() = credentials.clientId

    private val userId: String
        get() = credentials.userId

    private fun createOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder().apply {
            addInterceptor { chain ->
                val originalResponse = chain.proceed(chain.request())
                if (originalResponse.header("Content-Encoding") == "gzip") {
                    val gzipSource = GZIPInputStream(originalResponse.body.byteStream())
                    val decompressedBody = gzipSource.readBytes()
                        .toResponseBody(originalResponse.body.contentType())
                    originalResponse.newBuilder().body(decompressedBody).build()
                } else {
                    originalResponse
                }
            }
        }.build()
    }

    private val client: OkHttpClient by lazy { createOkHttpClient() }

    private fun getHeaders(): Headers {
        return Headers.Builder().apply {
            add("Accept", "application/json, text/javascript, */*; q=0.01")
            add("Accept-Language", "de,en-US;q=0.7,en;q=0.3")
            add("Accept-Encoding", "gzip")
            add("Referer", "https://soundcloud.com/")
            add("Authorization", "OAuth $accessToken")
            add("Origin", "https://soundcloud.com")
            add("Cache-Control", "no-cache")
            add("Connection", "keep-alive")
            add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:137.0) Gecko/20100101 Firefox/137.0")
        }.build()
    }

    suspend fun callApi(
        method: String
    ): String = withContext(Dispatchers.IO) {
        try {
            val url = HttpUrl.Builder()
                .scheme("https")
                .host("api-v2.soundcloud.com")
                .addPathSegments(method)
                .addQueryParameter("client_id", clientId)
                .build()

            println("FUCK YOU $url")

            val request = Request.Builder()
                .url(url)
                .apply {
                    if(method == "me") {
                        get()
                    } else {
                        post("".toRequestBody())
                    }
                    headers(getHeaders())
                }
                .build()

            client.newCall(request).await().use { response ->
                val responseBody = response.body.string()
                println("FUCK YOU $responseBody")
                responseBody
            }
        } catch (e: Exception) {
            throw e
        }
    }

    //<============= Login =============>

    suspend fun makeUser(token: String): User {
        try {
            val jsonData = callApi("me")
            val jsonObject = json.decodeFromString<JsonObject>(jsonData)
            val id = jsonObject["id"]?.jsonPrimitive?.content.orEmpty()
            session.updateCredentials(userId = id)
            return User(
                id = id,
                name = jsonObject["username"]?.jsonPrimitive?.content.orEmpty(),
                cover = jsonObject["avatar_url"]?.jsonPrimitive?.content.orEmpty().toImageHolder(),
                extras = mapOf(
                    "accessToken" to token,
                    "userId" to id,
                    "clientId" to clientId
                )
            )
        } catch (e: Exception) {
            throw e
        }
    }
}
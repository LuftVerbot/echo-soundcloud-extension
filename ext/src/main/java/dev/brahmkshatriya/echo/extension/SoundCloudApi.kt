package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.helpers.ContinuationCallback.Companion.await
import dev.brahmkshatriya.echo.common.models.ImageHolder.Companion.toImageHolder
import dev.brahmkshatriya.echo.common.models.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

class SoundCloudApi(private val session: SoundCloudSession) {

    private val json by lazy {
        Json {
            isLenient = true
            ignoreUnknownKeys = true
            useArrayPolymorphism = true
        }
    }

    private val credentials: SoundCloudCredentials
        get() = session.credentials

    val accessToken: String
        get() = credentials.accessToken

    private val clientId: String
        get() = credentials.clientId

    private val userId: String
        get() = credentials.userId

    private fun createOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder().build()
    }

    val client: OkHttpClient by lazy { createOkHttpClient() }

    private val staticHeaders: Headers by lazy {
        Headers.Builder().apply {
            add("Accept", "application/json, text/javascript, */*; q=0.01")
            add("Accept-Language", "de,en-US;q=0.7,en;q=0.3")
            add("Referer", "https://soundcloud.com/")
            add("Origin", "https://soundcloud.com")
            add("Cache-Control", "no-cache")
            add("Connection", "keep-alive")
            add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:137.0) Gecko/20100101 Firefox/137.0")
        }.build()
    }

    private fun getHeaders(): Headers {
        return staticHeaders.newBuilder().apply {
            add("Authorization", "OAuth $accessToken")
        }.build()
    }

    private suspend fun callApi(
        path: String,
        queryParams: Map<String, String> = emptyMap(),
        method: String = "GET",
        body: RequestBody? = null
    ): String = withContext(Dispatchers.IO) {
        val urlBuilder = HttpUrl.Builder()
            .scheme("https")
            .host("api-v2.soundcloud.com")
            .addPathSegments(path)
        queryParams.forEach { (k, v) -> urlBuilder.addQueryParameter(k, v) }
        urlBuilder.addQueryParameter("client_id", clientId)

        val url = urlBuilder.build()
        val reqBuilder = Request.Builder()
            .url(url)
            .apply {
                when (method) {
                    "GET"    -> get()
                    "POST"   -> post(body ?: "".toRequestBody())
                    "DELETE" -> delete()
                }
                headers(getHeaders())
            }

        client.newCall(reqBuilder.build()).await().use { response ->
            response.body.string()
        }
    }

    //<============= Login =============>

    suspend fun makeUser(token: String = accessToken): User {
        try {
            val jsonData = callApi("me")
            val jsonObject = decodeJson(jsonData)
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

    //<============= Home =============>

    suspend fun homePage(): JsonObject {
        val jsonData = callApi("mixed-selections")
        return decodeJson(jsonData)
    }

    //<============= Playlist =============>

    suspend fun getPlaylist(id: String): JsonObject =
        decodeJson(callApi("playlists/$id"))

    //<============= Track =============>

    suspend fun getTracks(ids: List<String>): JsonArray =
        json.decodeFromString(callApi("tracks", mapOf("ids" to ids.joinToString(","))))

    //<============= Search =============>

    suspend fun search(query: String, path: String): JsonObject =
        decodeJson(callApi("search/$path", mapOf("q" to query)))

    //<============= Util =============>

    suspend fun decodeJson(raw: String): JsonObject = withContext(Dispatchers.Default) {
        json.decodeFromString<JsonObject>(raw)
    }

    private fun buildParams(vararg pairs: Pair<String, String?>): Map<String, String> =
        pairs.mapNotNull { (k, v) -> v?.let { k to it } }.toMap()

}
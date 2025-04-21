package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.settings.Settings

class SoundCloudSession(
    var credentials: SoundCloudCredentials? = null,
    var settings: Settings? = null,

) {
    private val lock = Any()

    fun updateCredentials(
        accessToken: String? = null,
        clientId: String? = null,
        userId: String? = null
    ) {
        synchronized(lock) {
            val current = credentials ?: SoundCloudCredentials("", "", "")
            credentials = current.copy(
                accessToken = accessToken ?: current.accessToken,
                clientId = clientId ?: current.clientId,
                userId = userId ?: current.userId
            )
        }
    }

    companion object {
        @Volatile
        private var instance: SoundCloudSession? = null

        fun getInstance(): SoundCloudSession {
            return instance ?: synchronized(this) {
                instance ?: SoundCloudSession().also { instance = it }
            }
        }
    }
}

data class SoundCloudCredentials(
    val accessToken: String,
    val clientId: String,
    val userId: String
)
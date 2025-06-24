package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.settings.Settings
import java.util.concurrent.atomic.AtomicReference

class SoundCloudSession(
    var settings: Settings? = null,

) {

    private val credentialsRef = AtomicReference(
        SoundCloudCredentials("", "", "")
    )

    var credentials: SoundCloudCredentials
        get() = credentialsRef.get()
        private set(value) = credentialsRef.set(value)

    fun updateCredentials(
        accessToken: String? = null,
        clientId: String? = null,
        userId: String? = null
    ) {
        credentialsRef.updateAndGet { current ->
            current.copy(
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
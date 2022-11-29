package alerts.subscription

import alerts.HttpStatusCodeSerializer
import alerts.user.SlackUserId
import alerts.user.User
import alerts.user.UserId
import arrow.core.continuations.EffectScope
import com.github.avrokotlin.avro4k.AvroNamespace
import io.ktor.http.HttpStatusCode
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@JvmInline
value class RepositoryId(val serial: Long)

@Serializable
@AvroNamespace("alerts.domain.repository")
data class Repository(val owner: String, val name: String)

@Serializable
data class Subscriptions(val subscriptions: List<Subscription>)

@Serializable
data class Subscription(val repository: Repository, val subscribedAt: LocalDateTime)

@Serializable
@AvroNamespace("alerts.domain.subscription")
data class SubscriptionKey(val repository: Repository)

@Serializable
@AvroNamespace("alerts.domain.subscription")
data class SubscriptionEventRecord(val event: SubscriptionEvent)

@Serializable
enum class SubscriptionEvent { Created, Deleted; }

data class UserNotFound(val userId: User)

@Serializable
sealed interface SubscriptionError {
  fun toJson(): String = Json.encodeToString(serializer(), this)
}

@Serializable
data class RepoNotFound(
  val repository: Repository,
  @Serializable(HttpStatusCodeSerializer::class) val statusCode: HttpStatusCode? = null,
) : SubscriptionError

typealias MissingSlackUser = EffectScope<SlackUserNotFound>

@Serializable
data class SlackUserNotFound(val slackUserId: SlackUserId, val user: UserId? = null) : SubscriptionError

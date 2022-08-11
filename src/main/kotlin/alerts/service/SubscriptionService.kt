package alerts.service

import alerts.https.client.GithubClient
import alerts.kafka.SubscriptionProducer
import alerts.persistence.Repository
import alerts.persistence.SlackUserId
import alerts.persistence.Subscription
import alerts.persistence.SubscriptionsPersistence
import alerts.persistence.UserId
import alerts.persistence.UserPersistence
import arrow.core.Either
import arrow.core.continuations.either
import arrow.core.continuations.ensureNotNull
import io.ktor.http.HttpStatusCode
import mu.KotlinLogging

interface SubscriptionService {
  /** Returns all subscriptions for the given [slackUserId], empty if none found */
  suspend fun findAll(slackUserId: SlackUserId): Either<UserNotFound, List<Subscription>>
  
  /**
   * Subscribes to the provided [Subscription], only if the [Repository] exists.
   * If this is a **new** subscription for the user a [SubscriptionEvent.Created] event is sent.
   */
  suspend fun subscribe(slackUserId: SlackUserId, subscription: Subscription): Either<SubscriptionError, Unit>
  
  /**
   * Unsubscribes the repo. No-op if the [slackUserId] was not subscribed to the repo.
   * If the [Repository] has no more subscriptions a [SubscriptionEvent.Deleted] event is sent.
   */
  suspend fun unsubscribe(slackUserId: SlackUserId, repository: Repository): Either<UserNotFound, Unit>
}

sealed interface SubscriptionError
data class RepoNotFound(val repository: Repository, val statusCode: HttpStatusCode? = null) : SubscriptionError
data class UserNotFound(val slackUserId: SlackUserId, val user: UserId? = null) : SubscriptionError

fun SubscriptionService(
  subscriptions: SubscriptionsPersistence,
  users: UserPersistence,
  producer: SubscriptionProducer,
  client: GithubClient,
): SubscriptionService = Subscriptions(subscriptions, users, producer, client)

private class Subscriptions(
  private val subscriptions: SubscriptionsPersistence,
  private val users: UserPersistence,
  private val producer: SubscriptionProducer,
  private val client: GithubClient,
) : SubscriptionService {
  private val logger = KotlinLogging.logger { }
  
  override suspend fun findAll(slackUserId: SlackUserId): Either<UserNotFound, List<Subscription>> =
    either {
      val user = users.findSlackUser(slackUserId)
      ensureNotNull(user) { UserNotFound(slackUserId) }
      subscriptions.findAll(user.userId)
    }
  
  override suspend fun subscribe(
    slackUserId: SlackUserId,
    subscription: Subscription,
  ): Either<SubscriptionError, Unit> =
    either {
      val user = users.insertSlackUser(slackUserId)
      
      val exists = client.repositoryExists(subscription.repository.owner, subscription.repository.name)
        .mapLeft { RepoNotFound(subscription.repository, it.statusCode) }.bind()
      
      ensure(exists) { RepoNotFound(subscription.repository) }
      
      val hasSubscribers = subscriptions.findSubscribers(subscription.repository).isNotEmpty()
      
      subscriptions.subscribe(user.userId, subscription)
        .mapLeft { UserNotFound(slackUserId, it.userId) }.bind()
      
      if (!hasSubscribers) {
        producer.publish(subscription.repository)
      }
    }
  
  override suspend fun unsubscribe(slackUserId: SlackUserId, repository: Repository): Either<UserNotFound, Unit> =
    either {
      val user = ensureNotNull(users.findSlackUser(slackUserId)) { UserNotFound(slackUserId) }
      subscriptions.unsubscribe(user.userId, repository)
      val subscribers = subscriptions.findSubscribers(repository)
      if (subscribers.isEmpty()) producer.delete(repository)
    }
}

package alerts.persistence

import arrow.core.Either
import arrow.core.left
import arrow.core.nonFatalOrThrow
import arrow.core.right
import org.postgresql.util.PSQLException
import org.postgresql.util.PSQLState

fun PSQLException.isForeignKeyViolation(): Boolean =
  sqlState == PSQLState.FOREIGN_KEY_VIOLATION.state

fun PSQLException.isUniqueViolation(): Boolean =
  sqlState == PSQLState.UNIQUE_VIOLATION.state

/**
 * Catches a [Throwable], and allows mapping to [E].
 * In the case `null` is returned the original [Throwable] is rethrown,
 * otherwise `Either<E, A>` is returned.
 */
@Suppress("TooGenericExceptionCaught")
inline fun <reified T : Throwable, E, A> catch(
  block: () -> A,
  transform: (T) -> E,
): Either<E, A> = try {
  block().right()
} catch (e: Throwable) {
  val nonFatal = e.nonFatalOrThrow()
  if (nonFatal is T) {
    transform(nonFatal).left()
  } else throw e
}

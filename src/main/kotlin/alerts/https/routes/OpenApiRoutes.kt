package alerts.https.routes

import guru.zoroark.koa.ktor.respondOpenApiDocument
import guru.zoroark.koa.ktor.ui.swaggerUi
import io.ktor.server.application.call
import io.ktor.server.resources.get
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get

fun Routing.openApiRoutes() {
  get<Routes.OpenApi> { call.respondOpenApiDocument() }
  get<Routes.Swagger> { call.respondRedirect("/swagger/index.html") }
  swaggerUi("/swagger", "/openapi")
}

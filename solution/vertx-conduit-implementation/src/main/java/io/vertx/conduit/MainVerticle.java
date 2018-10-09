package io.vertx.conduit;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.jdbc.JDBCAuth;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.jwt.JWTOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

public class MainVerticle extends AbstractVerticle {

  private JDBCAuth authProvider;

  private JDBCClient jdbcClient;

  private JWTAuth jwtAuth;

  @Override
  public void start(Future<Void> future) {

    jdbcClient = JDBCClient.createShared(vertx, new JsonObject()
      .put("url", "jdbc:hsqldb:file:db/conduit;shutdown=true")
      .put("driver_class", "org.hsqldb.jdbcDriver")
      .put("user", "sa")
      .put("max_pool_size", 30));

    authProvider = JDBCAuth.create(vertx, jdbcClient);
    authProvider.setAuthenticationQuery("SELECT PASSWORD, PASSWORD_SALT FROM USER WHERE EMAIL = ?");

    // instantiate our JWT Auth Provider
    jwtAuth = JWTAuth.create(vertx, new JsonObject()
      .put("keyStore", new JsonObject()
        .put("type", "jceks")
        .put("path", "keystore.jceks")
        .put("password", "secret")));

    Router baseRouter = Router.router(vertx);
    baseRouter.route("/").handler(this::indexHandler);

    Router apiRouter = Router.router(vertx);
    apiRouter.route("/*").handler(BodyHandler.create());
    apiRouter.post("/users/login").handler(this::loginHandler);

    baseRouter.mountSubRouter("/api", apiRouter);

    vertx
      .createHttpServer()
      .requestHandler(baseRouter::accept)
      .listen(8080, result -> {
      if (result.succeeded()) {
        future.complete();
      }else {
        future.fail(result.cause());
      }
    });

  }

  private void loginHandler(RoutingContext context) {
    System.out.println(context.getBodyAsJson());
    JsonObject user = context.getBodyAsJson().getJsonObject("user");

    JsonObject authInfo = new JsonObject()
      .put("username", user.getString("email"))
      .put("password", user.getString("password"));

    HttpServerResponse response = context.response();

    authProvider.authenticate(authInfo, ar -> {
      if (ar.succeeded()) {

        // generate our JWT token
        String token = jwtAuth.generateToken(new JsonObject(), new JWTOptions().setIgnoreExpiration(true));

        JsonObject returnValue = new JsonObject()
          .put("user", new JsonObject()
            .put("email", "jake@jake.jake")
            .put("password", "jakejake")
            .put("token", token)
            .put("username", "jake")
            .put("bio", "I work at statefarm")
            .put("image", ""));
        System.out.println(returnValue);

        response.setStatusCode(200)
          .putHeader("Content-Type", "application/json; charset=utf-8")
          .putHeader("Content-Length", String.valueOf(returnValue.toString().length()))
          .end(returnValue.encode());
      }else{
        response.setStatusCode(200)
          .putHeader("Content-Type", "text/html")
          .end("Authentication Failed: " + ar.cause());
      }
    });
  }

  private void indexHandler(RoutingContext routingContext) {
    HttpServerResponse response = routingContext.response();
    response
      .putHeader("Content-Type", "text/html")
      .end("Hello, CodeOne!");
  }

}

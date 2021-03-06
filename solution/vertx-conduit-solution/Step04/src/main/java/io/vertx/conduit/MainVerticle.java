package io.vertx.conduit;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.jdbc.JDBCAuth;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.jwt.JWTOptions;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.JWTAuthHandler;

public class MainVerticle extends AbstractVerticle {

  public static final String HEADER_CONTENT_TYPE = "Content-Type";
  public static final String CONTENT_TYPE_JSON = "application/json; charset=utf-8";
  private static final String GET_USER_BY_EMAIL = "SELECT * FROM USER WHERE EMAIL = ?";

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

    JWTAuthHandler jwtAuthHandler = JWTAuthHandler.create(jwtAuth);

    Router baseRouter = Router.router(vertx);
    baseRouter.route("/").handler(this::indexHandler);

    Router apiRouter = Router.router(vertx);
    apiRouter.route("/*").handler(BodyHandler.create());
    apiRouter.post("/users/login").handler(this::loginHandler);
    apiRouter.get("/user").handler(jwtAuthHandler).handler(this::getUserHandler);

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

  private void getUserHandler(RoutingContext context) {

    String headerAuth = context.request().getHeader("Authorization");
    System.out.println("headerAuth: " + headerAuth);
    String[] values = headerAuth.split(" ");
    System.out.println("values[1]: " + values[1]);
    jwtAuth.authenticate(new JsonObject()
        .put("jwt", values[1]), res -> {
      if (res.succeeded()) {
        User theUser = res.result();
        JsonObject principal = theUser.principal();
        System.out.println("theUser: " + theUser.principal().encodePrettily());
      }else{
        //failed!
        System.out.println("authentication failed ");
      }

    });



    JsonObject returnValue = new JsonObject()
      .put("user", new JsonObject()
        .put("email", "jake@jake.jake")
        .put("password", "jakejake")
        .put("token", "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpYXQiOjE1MzkxMTMxNzJ9.EpFIxvzgsIhZmrEPQYhX9lzZgmpBiI1rgY9xl1YXOlc")
        .put("username", "jake")
        .put("bio", "I work at statefarm")
        .put("image", ""));
    context.response()
      .setStatusCode(200)
      .putHeader(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
      .end(returnValue.encodePrettily());
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

        System.out.println("authentication succeeded");

        // lookup the user
        jdbcClient.queryWithParams(GET_USER_BY_EMAIL, new JsonArray().add(ar.result().principal().getString("username")), fetch -> {

          if (fetch.succeeded()) {
            JsonObject completeUser = new JsonObject();
            ResultSet resultSet = fetch.result();
            if (resultSet.getNumRows() == 0) {
              context.fail(new Throwable("NOT FOUND"));
            }else{
              JsonArray rs = resultSet.getResults().get(0);
              System.out.println(completeUser);
              String token = jwtAuth.generateToken(completeUser, new JWTOptions().setIgnoreExpiration(true));
              response.setStatusCode(200)
                .putHeader("Content-Type", "application/json; charset=utf-8")
//                .putHeader("Content-Length", completeUser.toString().length())
                .end(completeUser.encodePrettily());

/*
              JsonArray row = resultSet.getResults().get(0);
              response.put("id", row.getInteger(0));
              response.put("rawContent", row.getString(1));
*/
            }
          }else{
            response.setStatusCode(204)
              .putHeader("Content-Type", "text/html")
              .end("Lookup Failed: " + fetch.cause());
          }
        });

        // generate our JWT token

/*
        System.out.println(ar.result().principal());
        JsonObject returnValue = new JsonObject()
          .put("user", new JsonObject()
            .put("email", "jake@jake.jake")
            .put("password", "jakejake")
            .put("token", token)
            .put("username", "jake")
            .put("bio", "I work at statefarm")
            .put("image", ""));
        System.out.println(returnValue);
*/

      }else{
        response.setStatusCode(401)
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

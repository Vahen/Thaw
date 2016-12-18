package fr.umlv.thaw.server;


import fr.umlv.thaw.channel.Channel;
import fr.umlv.thaw.channel.ChannelFactory;
import fr.umlv.thaw.database.Database;
import fr.umlv.thaw.database.DatabaseFactory;
import fr.umlv.thaw.logger.ThawLogger;
import fr.umlv.thaw.message.Message;
import fr.umlv.thaw.message.MessageFactory;
import fr.umlv.thaw.user.User;
import fr.umlv.thaw.user.humanUser.HumanUser;
import fr.umlv.thaw.user.humanUser.HumanUserFactory;
import fr.umlv.thaw.user.humanUser.HumanUserImpl;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.net.JksOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CookieHandler;
import io.vertx.ext.web.handler.SessionHandler;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.sstore.LocalSessionStore;
import sun.security.tools.keytool.CertAndKeyGen;
import sun.security.x509.X500Name;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;


/**
 * Project :Thaw
 * Created by Narex on 31/10/2016.
 */
public class Server extends AbstractVerticle {

    private static final int KB = 1024;
    private static final int MB = 1024 * KB;
    private final static int maxUploadSize = 50 * MB;
    private final List<Channel> channels;
    private final List<HumanUser> authorizedHumanUsers;
    private final List<User> connectedUsers;
    private final ThawLogger thawLogger;
    private final Database database;

    private final boolean ssl;

    public Server() throws IOException, SQLException, ClassNotFoundException {
        channels = new ArrayList<>();
        authorizedHumanUsers = new ArrayList<>();
        thawLogger = new ThawLogger(false);
        ssl = false;
        connectedUsers = new ArrayList<>();
        database = DatabaseFactory.createDatabase(Paths.get("../db"), "database");
    }

    /**
     * @param enableLogger Enable the logger
     * @param ssl          Enable ssl
     * @throws IOException If the logger can't find or create the file
     */
    public Server(boolean enableLogger, boolean ssl, Database database) throws IOException {
        channels = new ArrayList<>();
        authorizedHumanUsers = new ArrayList<>(); // Need to construct authorized list
        thawLogger = new ThawLogger(enableLogger);// Enable or not the logs of the server
        this.ssl = ssl;
        connectedUsers = new ArrayList<>();
        this.database = Objects.requireNonNull(database);
    }


    @Override
    public void start(Future<Void> fut) {
        // todo Load database stuff here


        // TEST ONLY //
        String hashPassword = Tools.toSHA256("password");
        String hashPassword2 = Tools.toSHA256("password2");

        HumanUserImpl superUser = HumanUserFactory.createHumanUser("superUser", hashPassword);
        HumanUserImpl test2 = HumanUserFactory.createHumanUser("test2", hashPassword2);
        authorizedHumanUsers.add(superUser);
        authorizedHumanUsers.add(test2);
//        connectedUsers.add(superUser);
        connectedUsers.add(test2);
        Channel defaul = ChannelFactory.createChannel(superUser, "default");
        Channel channel = ChannelFactory.createChannel(superUser, "Item 1");
        Channel channel2 = ChannelFactory.createChannel(superUser, "Item 2");

        Message mes = MessageFactory.createMessage(superUser, System.currentTimeMillis(), "1er lessage");
        Message mes1 = MessageFactory.createMessage(superUser, System.currentTimeMillis(), "2e message");
        Message mes2 = MessageFactory.createMessage(superUser, System.currentTimeMillis(), "3e message");
        defaul.addMessageToQueue(mes);
        defaul.addMessageToQueue(mes1);
        defaul.addMessageToQueue(mes2);

        superUser.joinChannel(defaul);
        test2.joinChannel(defaul);

        channels.add(defaul);
        channels.add(channel);
        channels.add(channel2);

/////////////////////////////////////////////////////////////////////////////////
        final int bindPort = 8080;
        Router router = Router.router(vertx);
        router.route().handler(CookieHandler.create());
        router.route().handler(BodyHandler.create().setBodyLimit(maxUploadSize));

        router.route().handler(SessionHandler.create(LocalSessionStore.create(vertx)));


        listOfRequest(router);
        router.route().handler(StaticHandler.create());
        if (ssl) {
            startSSLserver(fut, bindPort, router);
        } else {
            // No SSL requested, start a non-SSL HTTP server.
            startNonSSLserver(fut, bindPort, router);
        }
    }

    private void startNonSSLserver(Future<Void> fut, int bindPort, Router router) {
        vertx.createHttpServer().requestHandler(router::accept).listen(bindPort);
        thawLogger.log(Level.INFO, "Web server now listening");
        fut.complete();
    }

    private void startSSLserver(Future<Void> fut, int bindPort, Router router) {
        vertx.executeBlocking(future -> {
                    HttpServerOptions httpOpts = new HttpServerOptions();
                    generateKeyPairAndCertificate(fut);
                    httpOpts.setKeyStoreOptions(new JksOptions().setPath("./config/webserver/.keystore.jks").setPassword("password"));
                    httpOpts.setSsl(true);
                    future.complete(httpOpts);
                },
                (AsyncResult<HttpServerOptions> result) -> {
                    if (!result.failed()) {
                        vertx.createHttpServer(result.result()).requestHandler(router::accept).listen(bindPort);
                        thawLogger.log(Level.INFO, "SSL Web server now listening on port :" + bindPort);
                        fut.complete();
                    }
                });
    }

    private void generateKeyPairAndCertificate(Future<Void> fut) {
        try {
            // Generate a self-signed key pair and certificate.
            KeyStore store = KeyStore.getInstance("JKS");
            store.load(null, null);
            CertAndKeyGen keypair = new CertAndKeyGen("RSA", "SHA256WithRSA", null);
            X500Name x500Name = new X500Name("localhost", "IT", "unknown", "unknown", "unknown", "unknown");
            keypair.generate(1024);
            PrivateKey privKey = keypair.getPrivateKey();
            X509Certificate[] chain = new X509Certificate[1];
            chain[0] = keypair.getSelfCertificate(x500Name, new Date(), (long) 365 * 24 * 60 * 60);
            store.setKeyEntry("selfsigned", privKey, "password".toCharArray(), chain);
            try (FileOutputStream outputStream = new FileOutputStream("./config/webserver/.keystore.jks")) {
                store.store(outputStream, "password".toCharArray());
            }
        } catch (KeyStoreException | IOException | NoSuchAlgorithmException | CertificateException | NoSuchProviderException | InvalidKeyException | SignatureException ex) {
            thawLogger.log(Level.SEVERE, "Failed to generate a self-signed cert and other SSL configuration methods failed.");
            fut.fail(ex);
        }
    }

    // All requests that the server can use
    private void listOfRequest(Router router) {

        // No need of post or get for these
        router.route("/api/connectToServer").handler(routingContext -> Handlers.connectToServerHandle(routingContext, thawLogger, authorizedHumanUsers, connectedUsers, channels));
        router.route("/api/private/disconnectFromServer").handler(routingContext -> Handlers.disconnectFromServerHandle(routingContext, thawLogger, channels, connectedUsers));
        router.route("/api/createAccount").handler(routingContext -> Handlers.createAccountHandle(routingContext, thawLogger, authorizedHumanUsers, database));
        router.route("/api/private/*").handler(routingContext -> Handlers.securityCheckHandle(routingContext, thawLogger, authorizedHumanUsers));


        // Post & get requests
        router.post("/api/private/addChannel").handler(routingContext -> Handlers.addChannelHandle(routingContext, thawLogger, channels));
        router.post("/api/private/deleteChannel").handler(routingContext -> Handlers.deleteChannelHandle(routingContext, thawLogger, channels));
        router.post("/api/private/connectToChannel").handler(routingContext -> Handlers.connectToChannelHandle(routingContext, thawLogger, channels));
        router.post("/api/private/sendMessage").handler(routingContext -> Handlers.sendMessageHandle(routingContext, thawLogger, channels, database));
        router.post("/api/private/getListMessageForChannel").handler(routingContext -> Handlers.getListMessageForChannelHandle(routingContext, thawLogger, channels));
        router.post("/api/private/getListUserForChannel").handler(routingContext -> Handlers.getListUserForChannelHandle(routingContext, thawLogger, channels));
        router.get("/api/private/getListChannel").handler(routingContext -> Handlers.getListChannelHandle(routingContext, thawLogger, channels));

    }
}
package fr.umlv.thaw.main;

import fr.umlv.thaw.database.Database;
import fr.umlv.thaw.database.DatabaseFactory;
import fr.umlv.thaw.server.Server;
import io.vertx.core.Vertx;

import java.io.IOException;
import java.nio.file.Paths;
import java.sql.SQLException;


/**
 * The main application of our program.
 * This main is in charge of launching the routine
 * for the programme.
 */
public class Thaw {

    public static void main(String[] args) throws SQLException, ClassNotFoundException, IOException {

        Database database = DatabaseFactory.createDatabase(Paths.get("./db"));
        database.initializeDB();
        Server server = new Server(database);
        Vertx vertx = Vertx.vertx();
        vertx.deployVerticle(server);
    }
}

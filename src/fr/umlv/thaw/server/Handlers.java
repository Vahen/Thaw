package fr.umlv.thaw.server;

import fr.umlv.thaw.channel.Channel;
import fr.umlv.thaw.channel.ChannelFactory;
import fr.umlv.thaw.database.Database;
import fr.umlv.thaw.logger.ThawLogger;
import fr.umlv.thaw.message.Message;
import fr.umlv.thaw.message.MessageFactory;
import fr.umlv.thaw.user.User;
import fr.umlv.thaw.user.humanUser.HumanUser;
import fr.umlv.thaw.user.humanUser.HumanUserFactory;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.Session;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.stream.Collectors;

/*
*   To avoid stupid copy pasta for each methods, there is the list of reasons
*  that force us to write method that can contains more than 8 lines
*  even with delegation :
*
*   First, for a security reason, we must check every possible cases,
*  and because of that, our functions will be more and more consequent,
*  depending of the critical level of the method.
*   In a second hand, we also need to check our parameters contract and
*  that force us to write more and more test to be sure of the object
*  that we manipulate.
*   Finally, because we need to log  almost every events, we need to
*  try every possible case and write something depending of the event.
*
* */
class Handlers {

    // Order of handlers is same as the order of usage in the server

    /*##############################################################*/
    /////////////////// Connect to server Handler ///////////////////
    /*##############################################################*/


    static void connectToServerHandle(RoutingContext routingContext,
                                      ThawLogger thawLogger,
                                      List<HumanUser> authorizedHumanUsers,
                                      List<User> connectedUsers,
                                      List<Channel> channels) {
        thawLogger.log(Level.INFO, "In connectToServer request");
        HttpServerResponse response = routingContext.response();
        JsonObject json = routingContext.getBodyAsJson();
        Session session = routingContext.session();
        if (json == null) {
            answerToRequest(response, 400, "Wrong Json format", thawLogger);
        } else {
            analyzeConnectToServerRequest(session, response, json, thawLogger, authorizedHumanUsers, connectedUsers, channels);
        }
    }


    private static void analyzeConnectToServerRequest(Session session,
                                                      HttpServerResponse response,
                                                      JsonObject json,
                                                      ThawLogger thawLogger,
                                                      List<HumanUser> authorizedHumanUsers,
                                                      List<User> connectedUsers,
                                                      List<Channel> channels) {
        String userName = json.getString("userName");
        String password = json.getString("password");
        if (verifyEmptyOrNull(userName, password)) {
            answerToRequest(response, 400, "Wrong JSON input", thawLogger);
        }
        boolean containsUser = false;
        String passwordHash = Tools.toSHA256(password);
        HumanUser tmp = HumanUserFactory.createHumanUser(userName, passwordHash);
        for (HumanUser u : authorizedHumanUsers) {
            containsUser = connectedUsers.contains(u);
            if (containsUser && u.equals(tmp)) { //.getName().equals(userName) && u.compareHash(password)) {
                break;
            }

            if (containsUser && u.getName().equals(tmp.getName()) && !u.compareHash(tmp.getPasswordHash())) {
                thawLogger.log(Level.INFO, "User " + u.getName() + " went back & tried to connect with different password\nRemoving from connected user list");
                connectedUsers.remove(u);
                containsUser = connectedUsers.contains(u);
                break;
            }

            if (u.equals(tmp)) {
                connectedUsers.add(u);
                containsUser = connectedUsers.contains(u);
                session.put(u.getName(), u);
                session.put("user", u);
                break;
            }
        }
        if (session.get("user") == null || !containsUser) {
            answerToRequest(response, 400, "HumanUser: '" + userName + "' authentication failed", thawLogger);
        } else {
            Optional<Channel> optChannel = findChannelInList(channels, "general");
            if (!optChannel.isPresent()) {
                answerToRequest(response, 400, "Channel 'general' does not exist", thawLogger);
                return;
            }
            Channel chan = optChannel.get();
            User u = session.get("user");
            chan.addUserToChan(u);
            answerToRequest(response, 200, "HumanUser: '" + userName + "' authentication success, connected to 'general' channel", thawLogger);
        }
    }



    /*####################################################################*/
    /////////////////// Disconnect from server Handler ///////////////////
    /*####################################################################*/

    static void disconnectFromServerHandle(RoutingContext routingContext,
                                           ThawLogger thawLogger,
                                           List<Channel> channels,
                                           List<User> connectedUsers) {
        thawLogger.log(Level.INFO, "In disconnect from server request");
        HttpServerResponse response = routingContext.response();
        JsonObject json = routingContext.getBodyAsJson();
        if (json == null) {
            answerToRequest(response, 400, "Wrong JSON input", thawLogger);
        } else {
            analyzeDisconnectFromServerRequest(routingContext, response, json, thawLogger, channels, connectedUsers);
        }
    }


    private static void analyzeDisconnectFromServerRequest(RoutingContext routingContext,
                                                           HttpServerResponse response,
                                                           JsonObject json,
                                                           ThawLogger thawLogger,
                                                           List<Channel> channels,
                                                           List<User> connectedUsers) {

        String currentChannel = json.getString("currentChannelName");
        String userName = json.getString("userName");
        Session session = routingContext.session();
        if (verifyEmptyOrNull(currentChannel, userName)) {
            answerToRequest(response, 400, "There is no channel defined or the userName is incorrect", thawLogger);
            return;
        }
        Optional<Channel> optChannel = findChannelInList(channels, currentChannel);
        if (!optChannel.isPresent()) {
            answerToRequest(response, 400, "Channel '" + currentChannel + "' does not exist", thawLogger);
            return;
        }
        Channel chan = optChannel.get();
        HumanUser user = session.get(userName);
        if (user == null) {
            answerToRequest(response, 400, "Session for user does not exist", thawLogger);
            return;
        }
        connectedUsers.remove(user);
        chan.removeUserFromChan(user);
        // Destroy the HumanUser associated with the given userName. We don't stock any other value per user.
        routingContext.session().remove(userName);
        thawLogger.log(Level.INFO, "User '" + user.getName() + "' disconnected from server");
        String answer = Json.encodePrettily("Going back to login page");
        response.putHeader("location", "/").setStatusCode(200).end(answer);
    }



    /*############################################################*/
    /////////////////// Create Account Handler ///////////////////
    /*############################################################*/

    static void createAccountHandle(RoutingContext routingContext,
                                    ThawLogger thawLogger,
                                    List<HumanUser> authorizedHumanUsers,
                                    Database database) {
        thawLogger.log(Level.INFO, "In create account request");
        HttpServerResponse response = routingContext.response();
        JsonObject json = routingContext.getBodyAsJson();
        if (json == null) {
            answerToRequest(response, 400, "Wrong JSON input", thawLogger);
        } else {
            analyzeCreateAccountRequest(response, json, thawLogger, authorizedHumanUsers, database);
        }
    }

    private static void analyzeCreateAccountRequest(HttpServerResponse response,
                                                    JsonObject json,
                                                    ThawLogger thawLogger,
                                                    List<HumanUser> authorizedHumanUsers,
                                                    Database database) {
        String userName = json.getString("userName");
        String password = json.getString("password");
        boolean pattern = userName.matches("^[a-zA-Z][\\w]+$");//the password must be alphanumeric, start with letter, can contains _ and got at least 2 characters
        if (verifyEmptyOrNull(userName, password) || !pattern) {
            answerToRequest(response, 400, "Wrong JSON input", thawLogger);
            return;
        }
        String hashedPass = Tools.toSHA256(password);
        HumanUser humanUser = HumanUserFactory.createHumanUser(userName, hashedPass);
        if (authorizedHumanUsers.contains(humanUser)) {
            answerToRequest(response, 401, "User '" + userName + "' already exists", thawLogger);
            return;
        }
        try {
            database.createLogin(humanUser);
        } catch (SQLException e) {
            answerToRequest(response, 401, "User '" + userName + "' already exists", thawLogger);
            return;
        }
        authorizedHumanUsers.add(humanUser);
        for (Channel chan : database.getChannelList()) {
            try {
                database.addUserToChan(chan, humanUser, chan.getCreator());
            } catch (SQLException sql) {
                answerToRequest(response, 410, "User '" + userName + " cannot be added to the channel : " + chan.getChannelName(), thawLogger);
            }
        }
        answerToRequest(response, 200, "Account '" + userName + "' created", thawLogger);
    }

    /*############################################################*/
    /////////////////// Security Check Handler ///////////////////
    /*############################################################*/
    // Check if the user is connected to the server
    static void securityCheckHandle(RoutingContext routingContext,
                                    ThawLogger thawLogger,
                                    List<HumanUser> authorizedHumanUsers, List<User> connectedUsers) {
        thawLogger.log(Level.INFO, "In security check handler");
        Session session = routingContext.session();
        HttpServerResponse response = routingContext.response();
        HumanUser humanUser = session.get("user");
        if (humanUser == null || !authorizedHumanUsers.contains(humanUser) || !connectedUsers.contains(humanUser)) {
            answerToRequest(response, 403, "HumanUser does not have the access to private api ", thawLogger);
        } else {
            // Continue to the route we were before the check
            routingContext.next();
        }
    }



    /*########################################################*/
    /////////////////// Add Channel Handler ///////////////////
    /*########################################################*/

    static void addChannelHandle(RoutingContext routingContext,
                                 ThawLogger thawLogger,
                                 List<Channel> channels,
                                 Database database) {
        thawLogger.log(Level.INFO, "In addChannel request");
        HttpServerResponse response = routingContext.response();
        Session session = routingContext.session();
        JsonObject json = routingContext.getBodyAsJson();
        if (json == null) {
            answerToRequest(response, 400, "Wrong JSON input", thawLogger);
        } else {
            analyzeAddChannelRequest(session, response, json, thawLogger, channels, database);
        }
    }

    private static void analyzeAddChannelRequest(Session session,
                                                 HttpServerResponse response,
                                                 JsonObject json,
                                                 ThawLogger thawLogger,
                                                 List<Channel> channels,
                                                 Database database) {
        String newChannelName = json.getString("newChannelName");
        String creatorName = json.getString("creatorName");
        thawLogger.log(Level.INFO, newChannelName + " " + creatorName + " ");
        if (verifyEmptyOrNull(newChannelName, creatorName)) {
            answerToRequest(response, 400, "Wrong JSON input", thawLogger);
            return;
        }
        Optional<Channel> optChannel = findChannelInList(channels, newChannelName);
        if (optChannel.isPresent()) {
            answerToRequest(response, 400, "Channel " + newChannelName + " already exists", thawLogger);
        } else {
            HumanUser creator = session.get("user");
            newChannelName = newChannelName.trim();
            if (newChannelName.length() > 50 || !newChannelName.matches("^[\\w| ]+$")) {
                answerToRequest(response, 400, "The channelName exceed 50 characters or got not alphanumerics characters", thawLogger);
            } else {
                try {
                    Channel newChannel = ChannelFactory.createChannel(creator, newChannelName);
                    createAndAddChannel(newChannel, channels, database);
                    answerToRequest(response, 200, "Channel " + newChannelName + " successfully created", thawLogger);
                } catch (SQLException sql) {
                    answerToRequest(response, 400, "A SQLException has occurred during the creation of the channel : " + newChannelName, thawLogger);
                }
            }
        }
    }

    private static void createAndAddChannel(Channel newChannel,
                                            List<Channel> channels,
                                            Database database) throws SQLException {
        HumanUser creator = newChannel.getCreator();
        channels.add(newChannel);
        database.createChannelTable(newChannel);
        for (HumanUser usr : database.getAllUsersList()) {
            if (!usr.equals(creator)) {
                database.addUserToChan(newChannel, usr, creator);
            }
        }
    }



    /*############################################################*/
    /////////////////// Delete Channel Handler ///////////////////
    /*############################################################*/

    static void deleteChannelHandle(RoutingContext routingContext,
                                    ThawLogger thawLogger,
                                    List<Channel> channels, Database database) {
        thawLogger.log(Level.INFO, "In deleteChannel request");
        HttpServerResponse response = routingContext.response();
        JsonObject json = routingContext.getBodyAsJson();
        Session session = routingContext.session();

        if (json == null) {
            answerToRequest(response, 400, "Wrong Json format", thawLogger);
        } else {
            analyzeDeleteChannelRequest(response, session, json, thawLogger, channels, database);
        }
    }

    private static void analyzeDeleteChannelRequest(HttpServerResponse response,
                                                    Session session,
                                                    JsonObject json,
                                                    ThawLogger thawLogger,
                                                    List<Channel> channels, Database database) {

        String channelName = json.getString("channelName");
        String userName = json.getString("userName");
        if (verifyEmptyOrNull(channelName, userName)) {
            answerToRequest(response, 400, "Wrong JSON input", thawLogger);
            return;
        }
        Optional<Channel> optChannel = findChannelInList(channels, channelName);
        if (!optChannel.isPresent()) {
            answerToRequest(response, 400, "Channel '" + channelName + "' does not exist", thawLogger);
            return;
        }
        Channel channel = optChannel.get();
        HumanUser user = session.get(userName);
        if (user == null) {
            answerToRequest(response, 400, "User session not found", thawLogger);
            return;
        }

        if (!channel.isUserCreator(user)) {
            answerToRequest(response, 403, "You do not have the right to delete this channel", thawLogger);
            return;
        }
        if (channel.areUsersConnected()) {
            answerToRequest(response, 400, "Channel '" + channelName + "' failed to delete, users are still connected", thawLogger);
        } else {
            channels.remove(channel);
            try {
                database.removeUserAccessToChan(channel, user, user);
            } catch (SQLException e) {
                answerToRequest(response, 400, "Channel '" + channelName + "' failed to delete", thawLogger);
                return;
            }
            answerToRequest(response, 200, "Channel '" + channelName + "' successfully deleted", thawLogger);
        }
    }



    /*################################################################*/
    /////////////////// Connect to Channel Handler ///////////////////
    /*################################################################*/

    static void connectToChannelHandle(RoutingContext routingContext,
                                       ThawLogger thawLogger,
                                       List<Channel> channels) {
        thawLogger.log(Level.INFO, "In connectToChannel request");
        HttpServerResponse response = routingContext.response();
        JsonObject json = routingContext.getBodyAsJson();
        Session session = routingContext.session();
        if (json == null) {
            answerToRequest(response, 400, "Wrong Json format", thawLogger);
        } else {
            analyzeConnectToChannelRequest(response, session, json, thawLogger, channels);
        }
    }

    private static void analyzeConnectToChannelRequest(HttpServerResponse response,
                                                       Session session,
                                                       JsonObject json,
                                                       ThawLogger thawLogger,
                                                       List<Channel> channels) {
        String oldChannelName = json.getString("oldChannelName");
        String channelName = json.getString("channelName");
        String userName = json.getString("userName");
        if (verifyEmptyOrNull(oldChannelName, channelName, userName)) {
            answerToRequest(response, 400, "Wrong JSON input", thawLogger);
            return;
        }
        Optional<Channel> optChannel = findChannelInList(channels, channelName);
        if (!optChannel.isPresent()) {
            answerToRequest(response, 400, "Channel :" + channelName + " does not exist", thawLogger);
        } else {
            Channel chan = optChannel.get();
            HumanUser humanUser = session.get(userName);
            // Check if the user is already connected to the given channel
            if (chan.checkIfUserIsConnected(humanUser)) {
                answerToRequest(response, 400, "HumanUser :" + humanUser.getName() + " is already connected", thawLogger);
            } else {
                Optional<Channel> optChannelOld = findChannelInList(channels, oldChannelName);
                if (!optChannelOld.isPresent()) {
                    answerToRequest(response, 400, "OldChannel " + oldChannelName + " does not exist", thawLogger);
                } else {
                    Channel oldChan = optChannelOld.get();
                    if (establishConnection(humanUser, chan, oldChan)) {
                        String answer = "HumanUser :" + humanUser + " successfully quit channel :'" + oldChannelName + '\'' + " and connected to channel :'" + channelName + '\'';
                        answerToRequest(response, 200, answer, thawLogger);
                    } else {
                        String answer = "HumanUser :" + humanUser + " failed to quit or join channel";
                        answerToRequest(response, 400, answer, thawLogger);
                    }
                }
            }
        }
    }

    private static boolean establishConnection(HumanUser humanUser, Channel chan, Channel oldChan) {
        return oldChan.removeUserFromChan(humanUser) && chan.addUserToChan(humanUser);
    }



    /*##########################################################*/
    /////////////////// Send message Handler ///////////////////
    /*##########################################################*/

    static void sendMessageHandle(RoutingContext routingContext,
                                  ThawLogger thawLogger,
                                  List<Channel> channels,
                                  Database database) {
        thawLogger.log(Level.INFO, "In sendMessage request");
        JsonObject json = routingContext.getBodyAsJson();
        HttpServerResponse response = routingContext.response();
        Session session = routingContext.session();
        if (json == null) {
            answerToRequest(response, 400, "Wrong Json format", thawLogger);
        } else {
            analyzeSendMessageRequest(response, session, json, thawLogger, channels, database);
        }
    }

    private static void analyzeSendMessageRequest(HttpServerResponse response,
                                                  Session session,
                                                  JsonObject json,
                                                  ThawLogger thawLogger,
                                                  List<Channel> channels,
                                                  Database database) {
        long date = System.currentTimeMillis();
        String message = json.getString("message");//one of this characters will take now 4 characters
        String userName = json.getString("username");
        String channelName = json.getString("channelName");

        if (verifyEmptyOrNull(message, userName, channelName)) {
            answerToRequest(response, 400, "Wrong JSON input", thawLogger);
            return;
        }

        Optional<Channel> channelOptional = findChannelInList(channels, channelName);
        if (!channelOptional.isPresent()) {
            answerToRequest(response, 400, "Channel: '" + channelName + "' doesn't exist", thawLogger);
            return;
        }
        Channel chan = channelOptional.get();
        HumanUser humanUser = session.get(userName);

        if (!chan.checkIfUserIsConnected(humanUser)) {
            answerToRequest(response, 400, "HumanUser: '" + humanUser.getName() + "' is not connected to chan", thawLogger);
            return;
        }

        message = message.trim().replaceAll("<", "&lt;").replaceAll(">", "&gt;");
        if (message.length() > 512) {
            message = message.substring(0, 512);
        }
        Message mes = MessageFactory.createMessage(humanUser, date, message);

        try {
            database.addMessageToChannelTable(chan, mes);
        } catch (SQLException sql) {
            answerToRequest(response, 400, "Message from " + humanUser.getName() + " to the channel " + chan.getChannelName() + " hasn't been registered correctly", thawLogger);
            return;
        }


        answerToRequest(response, 200, "Message: " + mes + " sent correctly to channel '" + channelName + '\'', thawLogger);
    }



    /*##########################################################################*/
    /////////////////// Get list message for channel Handler ///////////////////
    /*##########################################################################*/

    static void getListMessageForChannelHandle(RoutingContext routingContext, ThawLogger thawLogger, List<Channel> channels, Database database) {
        thawLogger.log(Level.INFO, "In getListMessageForChannel request");
        HttpServerResponse response = routingContext.response();
        JsonObject json = routingContext.getBodyAsJson();
        if (json == null) {
            answerToRequest(response, 400, "Wrong Json format", thawLogger);
        } else {
            analyzeGetListMessageForChannelRequest(response, json, thawLogger, channels, database);
        }
    }

    private static void analyzeGetListMessageForChannelRequest(HttpServerResponse response, JsonObject json, ThawLogger thawLogger, List<Channel> channels, Database database) {
        String channelName = json.getString("channelName");
        Integer numberOfMessageWanted = json.getInteger("numberOfMessage");
        if (!securityCheckGetListMessageForChannel(response, channelName, numberOfMessageWanted, thawLogger)) {
            return;
        }
        Optional<Channel> optChan = findChannelInList(channels, channelName);
        if (optChan.isPresent()) {
            Channel channel = optChan.get();
            try {
                List<Message> tmpMess = database.getMessagesList(channel);
                List<Message> returnListMessage = tmpMess.subList(Math.max(tmpMess.size() - numberOfMessageWanted, 0), tmpMess.size());
                answerToRequest(response, 200, returnListMessage, thawLogger);
            } catch (SQLException sql) {
                answerToRequest(response, 400, "Problem for retrieving information at : " + channel.getChannelName() + " SQLException", thawLogger);
            }
        } else {
            answerToRequest(response, 400, "Channel: " + channelName + " doesn't exist", thawLogger);
        }
    }

    private static boolean securityCheckGetListMessageForChannel(HttpServerResponse response, String channelName, Integer numberOfMessageWanted, ThawLogger thawLogger) {
        if (verifyEmptyOrNull(channelName)) {
            answerToRequest(response, 400, "No channelName given", thawLogger);
            return false;
        }
        if (numberOfMessageWanted < 1) {
            answerToRequest(response, 400, "Number Of Message must be > 0 !", thawLogger);
            return false;
        }
        return true;
    }



    /*#######################################################################*/
    /////////////////// Get list user for channel Handler ///////////////////
    /*#######################################################################*/

    static void getListUserForChannelHandle(RoutingContext routingContext, ThawLogger thawLogger, List<Channel> channels) {
        thawLogger.log(Level.INFO, "In getListUserForChannel request");
        HttpServerResponse response = routingContext.response();
        JsonObject json = routingContext.getBodyAsJson();
        if (json == null) {
            routingContext.response().setStatusCode(400).end();
        } else {
            analyzeGetListUserForChannelRequest(response, json, thawLogger, channels);
        }
    }

    private static void analyzeGetListUserForChannelRequest(HttpServerResponse response, JsonObject json, ThawLogger thawLogger, List<Channel> channels) {
        String channelName = json.getString("channelName");
        if (!securityCheckGetListUserForChannel(response, channelName, thawLogger)) {
            return;
        }
        Optional<Channel> channelOptional = findChannelInList(channels, channelName);
        if (channelOptional.isPresent()) {
            List<String> tmp = channelOptional.get().getListUser().stream().map(User::getName).collect(Collectors.toList());
            answerToRequest(response, 200, tmp, thawLogger);
        } else {
            answerToRequest(response, 400, "Channel:" + channelName + " doesn't exist", thawLogger);
        }
    }

    private static boolean securityCheckGetListUserForChannel(HttpServerResponse response, String channelName, ThawLogger thawLogger) {
        if (verifyEmptyOrNull(channelName)) {
            answerToRequest(response, 400, "No channelName given", thawLogger);
            return false;
        }
        return true;
    }



    /*###############################################################*/
    /////////////////// Get list Channels Handler ///////////////////
    /*###############################################################*/

    static void getListChannelHandle(RoutingContext routingContext, ThawLogger thawLogger, List<Channel> channels) {
        thawLogger.log(Level.INFO, "In getListChannels request");
        HttpServerResponse response = routingContext.response();
        List<String> tmp = channels.stream().map(Channel::getChannelName).collect(Collectors.toList());
        answerToRequest(response, 200, tmp, thawLogger);
    }

    /*######################################################################*/
    /////////////////// Useful methods for all handlers ///////////////////
    /*######################################################################*/


    private static void answerToRequest(HttpServerResponse response, int code, Object answer, ThawLogger thawLogger) {
        String tmp = Json.encodePrettily(answer);
        if (code >= 200 && code < 300) {
            thawLogger.log(Level.INFO, "code: " + code + "\nanswer: " + tmp);
        } else {
            thawLogger.log(Level.WARNING, "code: " + code + "\nanswer: " + tmp);
        }

        response.setStatusCode(code)
                .putHeader("content-type", "application/json")
                .end(tmp);
    }

    private static Optional<Channel> findChannelInList(List<Channel> channels, String channelName) {
        if (verifyEmptyOrNull(channelName)) {
            return Optional.empty();
        }
        return channels.stream()
                .filter(c -> c.getChannelName().equals(channelName))
                .findFirst();
    }

    private static boolean verifyEmptyOrNull(String... strings) {
        for (String s : strings) {
            if (s == null || s.isEmpty()) {
                return true;
            }
        }
        return false;
    }
}

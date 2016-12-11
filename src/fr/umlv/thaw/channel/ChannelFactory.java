package fr.umlv.thaw.channel;

import fr.umlv.thaw.user.humanUser.HumanUser;

public class ChannelFactory {

    public static Channel createChannel(HumanUser creator, String channelName) {
        return new ChannelImpl(creator, channelName);
    }
}

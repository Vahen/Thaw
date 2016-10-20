package fr.umlv.thaw.user;

import fr.umlv.thaw.channel.Channel;

import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;

public class UserImpl implements User {

    private final String nickname;
    private final ConcurrentLinkedQueue<Channel> channels;

    public UserImpl(String nickname) {
        this.nickname = Objects.requireNonNull(nickname);
        channels = new ConcurrentLinkedQueue<>();
    }

    @Override
    public boolean sendMessage(long date, String message, Channel chan) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean joinChannel(Channel chan) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean quitChannel(Channel chan) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean addChannel(Channel chan) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean delChannel(Channel chan) {
        // TODO Auto-generated method stub
        return false;
    }

}

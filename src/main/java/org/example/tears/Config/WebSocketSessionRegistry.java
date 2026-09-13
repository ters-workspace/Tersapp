package org.example.tears.Config;

import org.example.tears.Model.User;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class WebSocketSessionRegistry {

    private final ConcurrentMap<String, User> sessions =
            new ConcurrentHashMap<>();

    public void register(String sessionId, User user) {
        sessions.put(sessionId, user);
    }

    public User getUser(String sessionId) {
        return sessions.get(sessionId);
    }

    public void remove(String sessionId) {
        sessions.remove(sessionId);
    }
}
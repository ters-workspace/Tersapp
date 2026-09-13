package org.example.tears.Service;

import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class PresenceService {

    private final ConcurrentMap<String, AtomicInteger> onlineUsers =
            new ConcurrentHashMap<>();


    public boolean online(String phone) {

        AtomicInteger connections =
                onlineUsers.computeIfAbsent(
                        phone,
                        key -> new AtomicInteger(0)
                );

        return connections.incrementAndGet() == 1;
    }


    public boolean offline(String phone) {

        AtomicInteger connections =
                onlineUsers.get(phone);

        if (connections == null) {
            return false;
        }

        if (connections.decrementAndGet() <= 0) {

            onlineUsers.remove(phone);

            return true;
        }

        return false;
    }


    public boolean isOnline(String phone) {

        AtomicInteger connections =
                onlineUsers.get(phone);

        return connections != null &&
                connections.get() > 0;
    }
}
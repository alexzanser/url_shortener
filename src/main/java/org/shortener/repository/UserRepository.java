package org.shortener.repository;

import org.shortener.model.ShortLink;

import java.util.*;

public class UserRepository {

    private final Map<UUID, List<ShortLink>> userLinksMap = new HashMap<>();

    public List<ShortLink> getUserLinks(UUID userId) {
        userLinksMap.putIfAbsent(userId, new ArrayList<>());
        return userLinksMap.get(userId);
    }

    public void addLinkToUser(UUID userId, ShortLink link) {
        getUserLinks(userId).add(link);
    }

    public void removeLinkFromUser(UUID userId, ShortLink link) {
        List<ShortLink> list = getUserLinks(userId);
        list.remove(link);
    }
}
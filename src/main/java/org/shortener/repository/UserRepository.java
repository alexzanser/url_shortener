package org.shortener.repository;

import org.shortener.model.ShortLink;

import java.util.*;

/**
 * Хранит, какие ссылки принадлежат какому пользователю:
 * userId -> List<ShortLink>
 */
public class UserRepository {

    private final Map<UUID, List<ShortLink>> userLinksMap = new HashMap<>();

    /**
     * Возвращает ссылки пользователя (список).
     * Если пользователя ещё нет, создаёт для него пустой список.
     */
    public List<ShortLink> getUserLinks(UUID userId) {
        userLinksMap.putIfAbsent(userId, new ArrayList<>());
        return userLinksMap.get(userId);
    }

    /**
     * Добавить ссылку пользователю
     */
    public void addLinkToUser(UUID userId, ShortLink link) {
        getUserLinks(userId).add(link);
    }

    /**
     * Удалить ссылку у пользователя
     */
    public void removeLinkFromUser(UUID userId, ShortLink link) {
        List<ShortLink> list = getUserLinks(userId);
        list.remove(link);
    }
}
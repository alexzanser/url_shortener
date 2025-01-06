package org.shortener.repository;

import org.shortener.model.ShortLink;

import java.util.HashMap;
import java.util.Map;

public class LinkRepository {
    private final Map<String, ShortLink> shortUrlMap = new HashMap<>();

    public void save(ShortLink link) {
        shortUrlMap.put(link.getShortUrl(), link);
    }

    public ShortLink findByShortUrl(String shortUrl) {
        return shortUrlMap.get(shortUrl);
    }

    public void delete(ShortLink link) {
        shortUrlMap.remove(link.getShortUrl());
    }

    public Map<String, ShortLink> findAll() {
        return shortUrlMap;
    }
}
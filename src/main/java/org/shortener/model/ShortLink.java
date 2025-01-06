package org.shortener.model;

import java.time.Instant;
import java.util.UUID;

public class ShortLink {
    private final String originalUrl;       // исходный URL
    private final String shortUrl;          // короткий URL (например, test.ru/AbCdE1)
    private final UUID userId;              // владелец ссылки (идентификатор пользователя)
    private final Instant creationTime;     // время создания
    private final Instant expirationTime;   // время истечения
    private int clickLimit;                 // лимит кликов
    private int clickCount;                 // текущее число переходов

    public ShortLink(String originalUrl, String shortUrl, UUID userId,
                     Instant creationTime, Instant expirationTime, int clickLimit) {
        this.originalUrl = originalUrl;
        this.shortUrl = shortUrl;
        this.userId = userId;
        this.creationTime = creationTime;
        this.expirationTime = expirationTime;
        this.clickLimit = clickLimit;
        this.clickCount = 0;
    }

    // Геттеры/сеттеры (где нужно)
    public String getOriginalUrl() { return originalUrl; }
    public String getShortUrl() { return shortUrl; }
    public UUID getUserId() { return userId; }
    public Instant getCreationTime() { return creationTime; }
    public Instant getExpirationTime() { return expirationTime; }
    public int getClickLimit() { return clickLimit; }
    public int getClickCount() { return clickCount; }

    public void setClickLimit(int clickLimit) {
        this.clickLimit = clickLimit;
    }

    public void incrementClickCount() {
        this.clickCount++;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expirationTime);
    }

    public boolean isClickLimitReached() {
        return clickCount >= clickLimit;
    }

    public boolean isAvailable() {
        return !isExpired() && !isClickLimitReached();
    }
}
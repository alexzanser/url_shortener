package org.shortener.service;

import org.shortener.config.AppConfig;
import org.shortener.model.ShortLink;
import org.shortener.repository.LinkRepository;
import org.shortener.repository.UserRepository;

import java.awt.Desktop;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class LinkService {

    private final AppConfig config;
    private final LinkRepository linkRepository;
    private final UserRepository userRepository;

    public LinkService(AppConfig config, LinkRepository linkRepository, UserRepository userRepository) {
        this.config = config;
        this.linkRepository = linkRepository;
        this.userRepository = userRepository;
    }

    public ShortLink createShortLink(UUID userId, String originalUrl, int userHours, int userClicks) {
        // 1. Приведение URL к формату (добавляем https:// если нужно)
        if (!originalUrl.startsWith("http://") && !originalUrl.startsWith("https://")) {
            originalUrl = "https://" + originalUrl;
        }

        // 2. Фактическое время жизни / лимит кликов
        int actualHours = Math.min(userHours, config.getDefaultLinkLifetimeHours());
        int actualClicks = Math.max(userClicks, config.getDefaultClickLimit());

        // 3. Генерация короткого URL
        String shortUrl = generateShortUrl();

        // 4. Формирование объекта ShortLink
        Instant now = Instant.now();
        Instant expirationTime = now.plus(Duration.ofHours(actualHours));
        ShortLink link = new ShortLink(
                originalUrl,
                shortUrl,
                userId,
                now,
                expirationTime,
                actualClicks
        );

        // 5. Сохраняем в LinkRepository и UserRepository
        linkRepository.save(link);
        userRepository.addLinkToUser(userId, link);

        return link;
    }

    private String generateShortUrl() {
        String chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        int length = 6;
        while (true) {
            StringBuilder sb = new StringBuilder(length);
            for (int i = 0; i < length; i++) {
                int randIndex = ThreadLocalRandom.current().nextInt(chars.length());
                sb.append(chars.charAt(randIndex));
            }
            String path = sb.toString();
            // Добавляем домен из конфигурации
            String candidate = config.getShortLinkDomain() + path;

            // Проверка на уникальность
            if (linkRepository.findByShortUrl(candidate) == null) {
                return candidate;
            }
        }
    }

    public void redirectToOriginalUrl(String shortUrl) {
        ShortLink link = linkRepository.findByShortUrl(shortUrl);
        if (link == null) {
            System.out.println("Ссылка не найдена или уже удалена.");
            return;
        }

        // Проверка на срок действия
        if (link.isExpired()) {
            System.out.println("Срок действия ссылки истёк! Ссылка будет автоматически удалена.");
            deleteLink(link);
            return;
        }

        // Проверка лимита кликов
        if (link.isClickLimitReached()) {
            System.out.println("Лимит переходов исчерпан! Ссылка будет автоматически удалена.");
            deleteLink(link);
            return;
        }

        // Увеличиваем счётчик и пытаемся открыть URL в браузере
        link.incrementClickCount();
        if (link.isClickLimitReached()) {
            System.out.println("Внимание! Это был последний доступный переход. Ссылка блокируется.");
        }

        try {
            Desktop.getDesktop().browse(new URI(link.getOriginalUrl()));
            System.out.println("Переход по ссылке " + link.getOriginalUrl() + " успешно выполнен!");
        } catch (Exception e) {
            System.out.println("Не удалось открыть браузер: " + e.getMessage());
        }
    }

    public void deleteLink(String shortUrl, UUID userId) {
        ShortLink link = linkRepository.findByShortUrl(shortUrl);
        if (link == null) {
            System.out.println("Ссылка не найдена.");
            return;
        }
        if (!link.getUserId().equals(userId)) {
            System.out.println("Вы не являетесь владельцем данной ссылки!");
            return;
        }
        deleteLink(link);
        System.out.println("Ссылка успешно удалена.");
    }

    private void deleteLink(ShortLink link) {
        linkRepository.delete(link);
        userRepository.removeLinkFromUser(link.getUserId(), link);
    }

    public void editClickLimit(String shortUrl, UUID userId, int newLimit) {
        ShortLink link = linkRepository.findByShortUrl(shortUrl);
        if (link == null) {
            System.out.println("Ссылка не найдена.");
            return;
        }
        if (!link.getUserId().equals(userId)) {
            System.out.println("Вы не являетесь владельцем данной ссылки!");
            return;
        }
        if (link.isExpired()) {
            System.out.println("Срок действия уже истёк, ссылка будет удалена.");
            deleteLink(link);
            return;
        }
        if (link.isClickLimitReached()) {
            System.out.println("Лимит кликов уже достигнут, ссылка будет удалена.");
            deleteLink(link);
            return;
        }

        int actualNewLimit = Math.max(newLimit, config.getDefaultClickLimit());
        link.setClickLimit(actualNewLimit);
        System.out.println("Новый лимит кликов: " + actualNewLimit);
    }

    public void listUserLinks(UUID userId) {
        List<ShortLink> links = userRepository.getUserLinks(userId);
        // Удалим просроченные
        List<ShortLink> toRemove = links.stream()
                .filter(ShortLink::isExpired)
                .collect(Collectors.toList());
        toRemove.forEach(this::deleteLink);

        if (links.isEmpty()) {
            System.out.println("У вас нет созданных ссылок (или все уже удалены).");
            return;
        }

        System.out.println("Ваши ссылки:");
        for (ShortLink link : links) {
            System.out.printf("ShortUrl: %s -> %s\n", link.getShortUrl(), link.getOriginalUrl());
            System.out.printf("  Лимит кликов: %d, Переходов: %d, Истекает: %s\n",
                    link.getClickLimit(), link.getClickCount(), link.getExpirationTime());
            System.out.printf("  Доступна: %s\n", link.isAvailable() ? "да" : "нет");
        }
    }

    public void cleanupExpiredLinks() {
        Map<String, ShortLink> all = linkRepository.findAll();
        List<ShortLink> toRemove = new ArrayList<>();
        for (ShortLink link : all.values()) {
            if (link.isExpired() || link.isClickLimitReached()) {
                toRemove.add(link);
            }
        }
        for (ShortLink link : toRemove) {
            deleteLink(link);
        }
    }
}
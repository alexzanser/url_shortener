package org.shortener;

import java.awt.Desktop;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Консольное приложение для сокращения ссылок,
 * где "короткая ссылка" выглядит как test.ru/AbCdE1
 */
public class Main {

    // ----------------------------------------------------------------------------------
    // Константа домена для коротких ссылок
    // ----------------------------------------------------------------------------------
    private static final String SHORT_DOMAIN = "test.ru/";

    // ----------------------------------------------------------------------------------
    // Структура данных, хранимая в памяти
    // ----------------------------------------------------------------------------------

    /**
     * Класс, описывающий короткую ссылку.
     */
    static class ShortLink {
        String originalUrl;      // исходный URL
        String shortUrl;         // короткий URL (например test.ru/AbCdE1)
        UUID userId;             // владелец ссылки
        Instant creationTime;    // время создания
        Instant expirationTime;  // время истечения
        int clickLimit;          // лимит кликов
        int clickCount;          // текущее число переходов

        ShortLink(
                String originalUrl,
                String shortUrl,
                UUID userId,
                Instant creationTime,
                Instant expirationTime,
                int clickLimit
        ) {
            this.originalUrl = originalUrl;
            this.shortUrl = shortUrl;
            this.userId = userId;
            this.creationTime = creationTime;
            this.expirationTime = expirationTime;
            this.clickLimit = clickLimit;
            this.clickCount = 0;
        }

        /**
         * Проверка: истекло ли время жизни?
         */
        boolean isExpired() {
            return Instant.now().isAfter(expirationTime);
        }

        /**
         * Проверка: достигнут ли лимит переходов?
         */
        boolean isClickLimitReached() {
            return clickCount >= clickLimit;
        }

        /**
         * Ссылка доступна, если не истекло время и не достигнут лимит кликов.
         */
        boolean isAvailable() {
            return !isExpired() && !isClickLimitReached();
        }
    }

    /**
     * Хранилище всех коротких ссылок: shortUrl -> ShortLink
     * (shortUrl теперь, к примеру, "test.ru/AbCd12")
     */
    static Map<String, ShortLink> shortUrlMap = new HashMap<>();

    /**
     * Для удобства: userId -> список ссылок пользователя
     */
    static Map<UUID, List<ShortLink>> userLinksMap = new HashMap<>();

    // ----------------------------------------------------------------------------------
    // Параметры по умолчанию — читаются из config.properties
    // ----------------------------------------------------------------------------------
    static int DEFAULT_LINK_LIFETIME_HOURS = 24; // заглушка, если не смогли прочитать
    static int DEFAULT_CLICK_LIMIT = 5;          // заглушка

    /**
     * Чтение настроек из файла config.properties
     */
    private static void loadConfig() {
        try (InputStream in = Main.class.getResourceAsStream("/config.properties")) {
            if (in == null) {
                System.out.println("Не удалось найти файл config.properties в ресурсах. Используются значения по умолчанию!");
                return;
            }
            Properties props = new Properties();
            props.load(in);

            DEFAULT_LINK_LIFETIME_HOURS = Integer.parseInt(
                    props.getProperty("default.link.lifetime.hours", "24")
            );
            DEFAULT_CLICK_LIMIT = Integer.parseInt(
                    props.getProperty("default.click.limit", "5")
            );

        } catch (IOException e) {
            System.out.println("Ошибка чтения config.properties: " + e.getMessage());
            // Оставляем дефолты
        } catch (NumberFormatException e) {
            System.out.println("Некорректное значение параметров в config.properties: " + e.getMessage());
        }
    }

    // ----------------------------------------------------------------------------------
    // Точка входа
    // ----------------------------------------------------------------------------------
    public static void main(String[] args) {
        loadConfig();
        try (Scanner scanner = new Scanner(System.in)) {
            System.out.println("Добро пожаловать в консольный URL-шортенер!");

            UUID currentUserId = null;
            boolean exit = false;

            while (!exit) {
                System.out.println("\nМеню:");
                System.out.println("1. Авторизация (ввести существующий UUID или сгенерировать новый)");
                System.out.println("2. Создать короткую ссылку");
                System.out.println("3. Перейти по короткой ссылке");
                System.out.println("4. Удалить ссылку");
                System.out.println("5. Изменить лимит переходов по ссылке");
                System.out.println("6. Просмотреть список своих ссылок");
                System.out.println("7. Выход");

                System.out.print("Выберите действие: ");
                String choice = scanner.nextLine();

                switch (choice) {
                    case "1" -> currentUserId = authorizeUser(scanner);
                    case "2" -> {
                        if (!checkUserLoggedIn(currentUserId)) break;
                        createShortLink(scanner, currentUserId);
                    }
                    case "3" -> {
                        System.out.print("Введите короткую ссылку (в формате test.ru/AbCdE1): ");
                        String shortUrlInput = scanner.nextLine();
                        redirectToOriginalUrl(shortUrlInput);
                    }
                    case "4" -> {
                        if (!checkUserLoggedIn(currentUserId)) break;
                        System.out.print("Введите короткую ссылку (test.ru/AbCdE1) для удаления: ");
                        String shortUrlInput = scanner.nextLine();
                        deleteShortLink(shortUrlInput, currentUserId);
                    }
                    case "5" -> {
                        if (!checkUserLoggedIn(currentUserId)) break;
                        System.out.print("Введите короткую ссылку (test.ru/AbCdE1) для изменения лимита: ");
                        String shortUrlInput = scanner.nextLine();
                        editClickLimit(scanner, shortUrlInput, currentUserId);
                    }
                    case "6" -> {
                        if (!checkUserLoggedIn(currentUserId)) break;
                        listUserLinks(currentUserId);
                    }
                    case "7" -> exit = true;
                    default -> System.out.println("Некорректный ввод, повторите.");
                }

                // Удаляем все просроченные ссылки (автоматическое удаление) в фоновом режиме
                cleanupExpiredLinks();
            }
        }

        System.out.println("Работа приложения завершена.");
    }

    /**
     * Меню авторизации. Пользователь может ввести уже существующий UUID
     * или сгенерировать новый.
     */
    private static UUID authorizeUser(Scanner scanner) {
        System.out.println("1. Ввести существующий UUID");
        System.out.println("2. Сгенерировать новый UUID");
        System.out.print("Выберите опцию: ");
        String choice = scanner.nextLine();
        switch (choice) {
            case "1" -> {
                System.out.print("Введите ваш UUID: ");
                String input = scanner.nextLine();
                try {
                    UUID uuid = UUID.fromString(input);
                    System.out.println("Добро пожаловать! Ваш userId = " + uuid);
                    // Если ранее userId не использовался — просто создадим для него запись.
                    userLinksMap.putIfAbsent(uuid, new ArrayList<>());
                    return uuid;
                } catch (IllegalArgumentException e) {
                    System.out.println("Некорректный формат UUID.");
                    return null;
                }
            }
            case "2" -> {
                UUID newUuid = UUID.randomUUID();
                System.out.println("Сгенерирован новый UUID: " + newUuid);
                userLinksMap.putIfAbsent(newUuid, new ArrayList<>());
                return newUuid;
            }
            default -> {
                System.out.println("Некорректный ввод, авторизация не выполнена.");
                return null;
            }
        }
    }

    /**
     * Проверяем, что пользователь авторизован. Если нет — сообщаем.
     */
    private static boolean checkUserLoggedIn(UUID userId) {
        if (userId == null) {
            System.out.println("Сначала необходимо авторизоваться (п.1 в меню).");
            return false;
        }
        return true;
    }

    /**
     * Создание короткой ссылки.
     * Пользователь вводит:
     * - исходную ссылку (originalUrl)
     * - время жизни (часов)
     * - лимит кликов
     *
     * По требованиям:
     * - фактическое время жизни = min(время_пользователя, DEFAULT_LINK_LIFETIME_HOURS)
     * - фактический лимит кликов = max(лимит_пользователя, DEFAULT_CLICK_LIMIT)
     */
    private static void createShortLink(Scanner scanner, UUID userId) {
        System.out.print("Введите исходную длинную ссылку (originalUrl): ");
        String originalUrl = scanner.nextLine().trim();
        if (!originalUrl.startsWith("http://") && !originalUrl.startsWith("https://")) {
            originalUrl = "https://" + originalUrl;
        }

        System.out.print("Введите желаемое время жизни ссылки (в часах): ");
        int userHours = readIntValue(scanner, 1);  // если ошибется — дадим дефолт 1
        int actualHours = Math.min(userHours, DEFAULT_LINK_LIFETIME_HOURS);

        System.out.print("Введите желаемый лимит кликов: ");
        int userClicks = readIntValue(scanner, 1);
        int actualClicks = Math.max(userClicks, DEFAULT_CLICK_LIMIT);

        // Генерируем "путь" (6 символов) + домен
        String shortUrl = generateShortUrl();
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

        shortUrlMap.put(shortUrl, link);
        userLinksMap.get(userId).add(link);

        System.out.println("\nКороткая ссылка успешно создана!");
        System.out.println("ShortUrl: " + shortUrl);
        System.out.println("Время жизни (часов): " + actualHours);
        System.out.println("Лимит кликов: " + actualClicks);
    }

    /**
     * Переход по короткой ссылке.
     * Проверяем доступность ссылки, увеличиваем счётчик кликов, открываем браузер.
     */
    private static void redirectToOriginalUrl(String shortUrl) {
        ShortLink link = shortUrlMap.get(shortUrl);
        if (link == null) {
            System.out.println("Ссылка не найдена или устарела.");
            return;
        }

        // Сначала проверим не просрочена ли
        if (link.isExpired()) {
            System.out.println("Срок действия ссылки истёк! Ссылка будет автоматически удалена.");
            removeLinkFromMaps(link);
            return;
        }

        // Проверим лимит кликов
        if (link.isClickLimitReached()) {
            System.out.println("Лимит переходов исчерпан! Ссылка будет автоматически удалена.");
            removeLinkFromMaps(link);
            return;
        }

        // Всё хорошо, увеличиваем счётчик и открываем в браузере
        link.clickCount++;
        // Если только что достигли лимита, предупредим
        if (link.isClickLimitReached()) {
            System.out.println("Внимание! Это был последний доступный переход. Ссылка сейчас блокируется.");
        }

        try {
            Desktop.getDesktop().browse(new URI(link.originalUrl));
            System.out.println("Переход по ссылке " + link.originalUrl + " успешно выполнен!");
        } catch (Exception e) {
            System.out.println("Не удалось открыть браузер: " + e.getMessage());
        }
    }

    /**
     * Удаление ссылки (по желанию пользователя).
     * Доступно только владельцу.
     */
    private static void deleteShortLink(String shortUrl, UUID userId) {
        ShortLink link = shortUrlMap.get(shortUrl);
        if (link == null) {
            System.out.println("Ссылка не найдена или уже удалена.");
            return;
        }
        if (!link.userId.equals(userId)) {
            System.out.println("Вы не являетесь владельцем данной ссылки!");
            return;
        }
        removeLinkFromMaps(link);
        System.out.println("Ссылка успешно удалена.");
    }

    /**
     * Изменить лимит кликов.
     * Доступно только владельцу.
     * Новый лимит = max(введённый_пользователем, DEFAULT_CLICK_LIMIT).
     */
    private static void editClickLimit(Scanner scanner, String shortUrl, UUID userId) {
        ShortLink link = shortUrlMap.get(shortUrl);
        if (link == null) {
            System.out.println("Ссылка не найдена.");
            return;
        }
        if (!link.userId.equals(userId)) {
            System.out.println("Вы не являетесь владельцем данной ссылки!");
            return;
        }

        // Проверим, не просрочена ли уже
        if (link.isExpired()) {
            System.out.println("Срок действия ссылки уже истёк! Ссылка будет удалена.");
            removeLinkFromMaps(link);
            return;
        }

        // Проверим, не достигнут ли лимит
        if (link.isClickLimitReached()) {
            System.out.println("Лимит кликов уже достигнут! Ссылка будет удалена.");
            removeLinkFromMaps(link);
            return;
        }

        System.out.print("Введите новый лимит кликов: ");
        int newLimit = readIntValue(scanner, 1);
        int actualNewLimit = Math.max(newLimit, DEFAULT_CLICK_LIMIT);

        link.clickLimit = actualNewLimit;
        System.out.println("Новый лимит кликов: " + actualNewLimit);
    }

    /**
     * Вывод списка ссылок пользователя.
     * Одновременно чистим просроченные.
     */
    private static void listUserLinks(UUID userId) {
        List<ShortLink> links = userLinksMap.get(userId);
        if (links == null || links.isEmpty()) {
            System.out.println("У вас нет созданных ссылок.");
            return;
        }

        // Удалим просроченные перед показом
        links.removeIf(link -> {
            if (link.isExpired()) {
                shortUrlMap.remove(link.shortUrl);
                return true;
            }
            return false;
        });

        if (links.isEmpty()) {
            System.out.println("Все ваши ссылки просрочены или удалены.");
            return;
        }

        System.out.println("Ваши ссылки:");
        for (ShortLink link : links) {
            System.out.printf("ShortUrl: %s -> %s%n", link.shortUrl, link.originalUrl);
            System.out.printf("  Лимит кликов: %d, Переходов: %d, Истекает: %s%n",
                    link.clickLimit, link.clickCount, link.expirationTime);
            // Можно дополнительно отметить, доступна ли ссылка в данный момент
            System.out.printf("  Доступна: %s%n", link.isAvailable() ? "да" : "нет");
        }
    }

    /**
     * Генерация короткой ссылки (домен + 6 случайных символов),
     * пока не найдём уникальное значение, не занятое в shortUrlMap.
     *
     * Пример: "test.ru/AbCdE1"
     */
    private static String generateShortUrl() {
        String chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        int length = 6;
        while (true) {
            StringBuilder sb = new StringBuilder(length);
            for (int i = 0; i < length; i++) {
                int randIndex = ThreadLocalRandom.current().nextInt(chars.length());
                sb.append(chars.charAt(randIndex));
            }
            String path = sb.toString();
            String candidate = SHORT_DOMAIN + path;  // "test.ru/AbCdE1"

            // Проверка на уникальность
            if (!shortUrlMap.containsKey(candidate)) {
                return candidate;
            }
        }
    }

    /**
     * Удаляем ссылку отовсюду.
     */
    private static void removeLinkFromMaps(ShortLink link) {
        shortUrlMap.remove(link.shortUrl);
        List<ShortLink> userLinks = userLinksMap.get(link.userId);
        if (userLinks != null) {
            userLinks.remove(link);
        }
    }

    /**
     * Удаляем все просроченные или &laquo;выработанные&raquo; ссылки.
     * Запускается после каждого действия.
     */
    private static void cleanupExpiredLinks() {
        List<String> toRemove = new ArrayList<>();
        for (var entry : shortUrlMap.entrySet()) {
            ShortLink link = entry.getValue();
            if (link.isExpired() || link.isClickLimitReached()) {
                toRemove.add(entry.getKey());
            }
        }
        for (String shortUrl : toRemove) {
            ShortLink link = shortUrlMap.get(shortUrl);
            if (link != null) {
                removeLinkFromMaps(link);
            }
        }
    }

    /**
     * Чтение целого числа из консоли (с защитой от неправильного ввода).
     * Возвращаем дефолтное значение, если пользователь вводит ерунду.
     */
    private static int readIntValue(Scanner scanner, int defaultValue) {
        try {
            String input = scanner.nextLine();
            return Integer.parseInt(input);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

}
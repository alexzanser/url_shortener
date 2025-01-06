package org.shortener.cli;

import org.shortener.config.AppConfig;
import org.shortener.model.ShortLink;
import org.shortener.repository.LinkRepository;
import org.shortener.repository.UserRepository;
import org.shortener.service.LinkService;
import org.shortener.service.UserService;

import java.util.Scanner;
import java.util.UUID;

public class Main {

    public static void main(String[] args) {
        // 1) Создаём объект конфигурации
        AppConfig appConfig = new AppConfig();

        // 2) Создаём репозитории и сервисы
        LinkRepository linkRepository = new LinkRepository();
        UserRepository userRepository = new UserRepository();
        LinkService linkService = new LinkService(appConfig, linkRepository, userRepository);
        UserService userService = new UserService(userRepository);

        // 3) Запускаем консольный интерфейс
        System.out.println("Добро пожаловать в консольный URL-шортенер!");

        UUID currentUserId = null;
        boolean exit = false;

        try (Scanner scanner = new Scanner(System.in)) {
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
                    case "1" -> {
                        currentUserId = userService.authorizeUser(scanner);
                    }
                    case "2" -> {
                        if (!userService.checkUserLoggedIn(currentUserId)) break;
                        createShortLinkFlow(scanner, currentUserId, linkService);
                    }
                    case "3" -> {
                        System.out.print("Введите короткую ссылку (test.ru/AbCdE1): ");
                        String shortUrlInput = scanner.nextLine();
                        linkService.redirectToOriginalUrl(shortUrlInput);
                    }
                    case "4" -> {
                        if (!userService.checkUserLoggedIn(currentUserId)) break;
                        System.out.print("Введите короткую ссылку (test.ru/AbCdE1) для удаления: ");
                        String shortUrlInput = scanner.nextLine();
                        linkService.deleteLink(shortUrlInput, currentUserId);
                    }
                    case "5" -> {
                        if (!userService.checkUserLoggedIn(currentUserId)) break;
                        System.out.print("Введите короткую ссылку (test.ru/AbCdE1) для изменения лимита: ");
                        String shortUrlInput = scanner.nextLine();
                        System.out.print("Введите новый лимит кликов: ");
                        int newLimit = readIntValue(scanner, 1);
                        linkService.editClickLimit(shortUrlInput, currentUserId, newLimit);
                    }
                    case "6" -> {
                        if (!userService.checkUserLoggedIn(currentUserId)) break;
                        linkService.listUserLinks(currentUserId);
                    }
                    case "7" -> exit = true;
                    default -> System.out.println("Некорректный ввод, повторите.");
                }

                // Автоматическая очистка просроченных ссылок
                linkService.cleanupExpiredLinks();
            }
        }
        System.out.println("Работа приложения завершена.");
    }

    private static void createShortLinkFlow(Scanner scanner, UUID userId, LinkService linkService) {
        System.out.print("Введите исходную длинную ссылку (originalUrl): ");
        String originalUrl = scanner.nextLine().trim();

        System.out.print("Введите желаемое время жизни ссылки (в часах): ");
        int userHours = readIntValue(scanner, 1);

        System.out.print("Введите желаемый лимит кликов: ");
        int userClicks = readIntValue(scanner, 1);

        // Создаём
        ShortLink link = linkService.createShortLink(userId, originalUrl, userHours, userClicks);

        // Выводим информацию
        System.out.println("\nКороткая ссылка успешно создана!");
        System.out.println("ShortUrl: " + link.getShortUrl());
        System.out.println("Время жизни (часов): " + (link.getExpirationTime().getEpochSecond()
                - link.getCreationTime().getEpochSecond()) / 3600);
        System.out.println("Лимит кликов: " + link.getClickLimit());
    }

    private static int readIntValue(Scanner scanner, int defaultValue) {
        try {
            String input = scanner.nextLine();
            return Integer.parseInt(input);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
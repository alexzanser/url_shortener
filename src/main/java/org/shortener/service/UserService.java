package org.shortener.service;

import org.shortener.repository.UserRepository;

import java.util.List;
import java.util.Scanner;
import java.util.UUID;

public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UUID authorizeUser(Scanner scanner) {
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
                    // Убедимся, что у пользователя есть список (создадим при необходимости).
                    userRepository.getUserLinks(uuid);
                    return uuid;
                } catch (IllegalArgumentException e) {
                    System.out.println("Некорректный формат UUID.");
                    return null;
                }
            }
            case "2" -> {
                UUID newUuid = UUID.randomUUID();
                System.out.println("Сгенерирован новый UUID: " + newUuid);
                userRepository.getUserLinks(newUuid);
                return newUuid;
            }
            default -> {
                System.out.println("Некорректный ввод, авторизация не выполнена.");
                return null;
            }
        }
    }

    public boolean checkUserLoggedIn(UUID userId) {
        if (userId == null) {
            System.out.println("Сначала необходимо авторизоваться (п.1 в меню).");
            return false;
        }
        return true;
    }
}
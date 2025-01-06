package org.shortener.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class AppConfig {

    private static final String CONFIG_FILE = "/config.properties";

    // Значения по умолчанию, если не смогли прочитать
    private int defaultLinkLifetimeHours = 24;
    private int defaultClickLimit = 5;
    private String shortLinkDomain = "test.ru/";

    public AppConfig() {
        loadProperties();
    }

    private void loadProperties() {
        try (InputStream in = AppConfig.class.getResourceAsStream(CONFIG_FILE)) {
            if (in == null) {
                System.out.println("Файл config.properties не найден, используем значения по умолчанию.");
                return;
            }
            Properties props = new Properties();
            props.load(in);

            // Чтение значений
            defaultLinkLifetimeHours = Integer.parseInt(
                    props.getProperty("default.link.lifetime.hours", "24")
            );
            defaultClickLimit = Integer.parseInt(
                    props.getProperty("default.click.limit", "5")
            );
            shortLinkDomain = props.getProperty("short.link.domain", "test.ru/");

        } catch (IOException | NumberFormatException e) {
            System.out.println("Ошибка чтения config.properties: " + e.getMessage());
        }
    }

    public int getDefaultLinkLifetimeHours() {
        return defaultLinkLifetimeHours;
    }

    public int getDefaultClickLimit() {
        return defaultClickLimit;
    }

    public String getShortLinkDomain() {
        return shortLinkDomain;
    }
}
package org.example.config;

public class DatabaseConfig {

    private final String dbUrl;
    private final String dbUser;
    private final String dbPassword;

    public DatabaseConfig(String dbUrl, String dbUser, String dbPassword) {
        this.dbUrl = dbUrl;
        this.dbUser = dbUser;
        this.dbPassword = dbPassword;
    }

    public static DatabaseConfig fromEnvironment() {
        String dbUrl = requireEnvironmentVariable("DB_URL");
        String dbUser = requireEnvironmentVariable("DB_USER");
        String dbPassword = requireEnvironmentVariable("DB_PASSWORD");

        return new DatabaseConfig(dbUrl, dbUser, dbPassword);
    }

    private static String requireEnvironmentVariable(String variableName) {
        String value = System.getenv(variableName);

        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required environment variable: " + variableName);
        }

        return value;
    }

    public String getDbUrl() {
        return dbUrl;
    }

    public String getDbUser() {
        return dbUser;
    }

    public String getDbPassword() {
        return dbPassword;
    }
}
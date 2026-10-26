package org.example.repository;

import org.example.config.DatabaseConfig; // [router-3] containerul injectează configurația, nu trei String-uri
import org.example.exception.DataAccessException;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class CategoryRepository {
    private final String url;
    private final String user;
    private final String password;

    public CategoryRepository(DatabaseConfig databaseConfig) { // [router-3] o singură dependență, de un tip pe care containerul îl poate găsi
        this.url = databaseConfig.getDbUrl(); // [router-3] URL-ul bazei de date, citit din configurație
        this.user = databaseConfig.getDbUser(); // [router-3] utilizatorul, citit din configurație
        this.password = databaseConfig.getDbPassword(); // [router-3] parola, citită din configurație
    } // [router-3] sfârșitul constructorului

    public boolean existsById(int categoryId) {
        String sql = "SELECT id " +
                "FROM category " +
                "WHERE id = ?";

        try (Connection connection = DriverManager.getConnection(url, user, password);
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, categoryId);

            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException ex) {
            throw new DataAccessException("Could not check whether category exists", ex);
        }
    }
}
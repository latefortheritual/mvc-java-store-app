package org.example.repository;

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

    public CategoryRepository(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

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
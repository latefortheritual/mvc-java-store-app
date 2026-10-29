package org.example.repository;

import org.example.exception.DataAccessException;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class BrandRepository {

    private final String url;
    private final String user;
    private final String password;

    public BrandRepository(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

    public boolean existsById(int brandId) {
        String sql = "SELECT id " +
                "FROM brand " +
                "WHERE id = ?";

        try (Connection connection = DriverManager.getConnection(url, user, password);
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, brandId);

            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException ex) {
            throw new DataAccessException("Could not check whether brand exists", ex);
        }
    }
}
package org.example.repository;

import org.example.dto.CreateProductRequest;
import org.example.exception.DataAccessException;
import org.example.model.Product;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ProductRepository {
    private final String url;
    private final String user;
    private final String password;

    public ProductRepository(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

    public Product createProduct(CreateProductRequest request) {
        String sql = "INSERT INTO product(name, brand_id, category_id, price) " +
                "VALUES (?, ?, ?, ?) " +
                "RETURNING id, name, brand_id, category_id, price ";

        try (Connection connection = DriverManager.getConnection(url, user, password);
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, request.name());
            statement.setInt(2, request.brandId());
            statement.setInt(3, request.categoryId());
            statement.setBigDecimal(4, request.price());

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException("Product was inserted, but no row was returned");
                }
                return mapRow(resultSet);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not create products", e);

        }

    }

    public List<Product> listProduct(int limit, long offset) {
        String sql = "SELECT * " +
                "FROM product " +
                "ORDER BY id " +
                "LIMIT ? OFFSET ?";
        List<Product> products = new ArrayList<>();

        try (Connection connection = DriverManager.getConnection(url, user, password);
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, limit);
            statement.setLong(2, offset);

            ResultSet resultSet = statement.executeQuery();
            while (resultSet.next()) {
                products.add(mapRow(resultSet));
            }

        } catch (SQLException e) {
            throw new DataAccessException("Could not load products", e);
        }
        return products;
    }

    private Product mapRow(ResultSet resultSet) throws SQLException {
        return new Product(
                resultSet.getInt("id"),
                resultSet.getString("name"),
                resultSet.getInt("brand_id"),
                resultSet.getInt("category_id"),
                resultSet.getBigDecimal("price")
        );
    }
}
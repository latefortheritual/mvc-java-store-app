package org.example.repository;

import org.example.config.DatabaseConfig; // [router-3] containerul injectează configurația, nu trei String-uri
import org.example.dto.MobileProductsResponse;
import org.example.dto.PatchProductRequest;
import org.example.dto.ProductWriteRequest;
import org.example.exception.DataAccessException;
import org.example.model.Product;

import java.sql.*;
import java.util.*;

public class ProductRepository {
    private final String url;
    private final String user;
    private final String password;

    public ProductRepository(DatabaseConfig databaseConfig) { // [router-3] o singură dependență, de un tip pe care containerul îl poate găsi
        this.url = databaseConfig.getDbUrl(); // [router-3] URL-ul bazei de date, citit din configurație
        this.user = databaseConfig.getDbUser(); // [router-3] utilizatorul, citit din configurație
        this.password = databaseConfig.getDbPassword(); // [router-3] parola, citită din configurație
    } // [router-3] sfârșitul constructorului

    public Product createProduct(ProductWriteRequest request) {
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

    public List<Product> readProduct(int limit, long offset) {
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

    public Optional<Product> readById(int productId) {
        String sql = "SELECT * " +
                "FROM product " +
                "WHERE id=?";
        try (Connection connection = DriverManager.getConnection(url, user, password);
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, productId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    Product product = mapRow(resultSet);

                    return Optional.of(product);
                }

                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not load product", e);
        }
    }

    public Optional<Product> updateById(int productId, ProductWriteRequest request) {
        String sql = "UPDATE product " +
                "SET name = ?, " +
                "    brand_id = ?, " +
                "    category_id = ?, " +
                "    price = ? " +
                "WHERE id = ? " +
                "RETURNING id, name, brand_id, category_id, price";

        try (Connection connection = DriverManager.getConnection(url, user, password);
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, request.name());
            statement.setInt(2, request.brandId());
            statement.setInt(3, request.categoryId());
            statement.setBigDecimal(4, request.price());
            statement.setInt(5,productId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    Product product = mapRow(resultSet);

                    return Optional.of(product);
                }

                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not update product", e);
        }
    }

    public Optional<Product> updateFieldsById(int productId, PatchProductRequest request) {

        List<String> columns = new ArrayList<>();
        if(request.name() != null){
            columns.add("name = ?");
        }
        if(request.brandId() != null){
            columns.add("brand_id = ?");
        }
        if(request.categoryId() != null){
            columns.add("category_id = ?");
        }
        if(request.price() != null){
            columns.add("price = ?");
        }
        String setClause = String.join(", ", columns);


        String sql = "UPDATE product " +
                "SET " + setClause + " " +
                "WHERE id = ? " +
                "RETURNING id, name, brand_id, category_id, price";

        try (Connection connection = DriverManager.getConnection(url, user, password);
             PreparedStatement statement = connection.prepareStatement(sql)) {

            int index = 1;
            if(request.name() != null){
                statement.setString(index, request.name());
                index ++;
            }
            if(request.brandId() != null){
                statement.setInt(index, request.brandId());
                index ++;
            }
            if(request.categoryId() != null){
                statement.setInt(index, request.categoryId());
                index ++;
            }
            if(request.price() != null){
                statement.setBigDecimal(index, request.price());
                index ++;
            }
            statement.setInt(index,productId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    Product product = mapRow(resultSet);

                    return Optional.of(product);
                }

                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not update product", e);
        }
    }

    public Optional<Product> deleteById(int productId) {
        String sql = "DELETE FROM product " +
                "WHERE id = ? " +
                "RETURNING id, name, brand_id, category_id, price";
        try (Connection connection = DriverManager.getConnection(url, user, password);
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, productId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    Product deletedProduct = mapRow(resultSet);

                    return Optional.of(deletedProduct);
                }

                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not delete product", e);
        }
    }

    public MobileProductsResponse readMobileProducts(){
        String sql = "SELECT p.id, p.name, p.price, b.id AS brand_id, b.name AS brand_name, c.id AS category_id, c.name AS category_name " +
                "FROM product p " +
                "INNER JOIN brand b ON p.brand_id = b.id " +
                "INNER JOIN category c ON p.category_id = c.id " +
                "ORDER BY p.id " +
                "LIMIT 100";
        List<Product> products = new ArrayList<>();
        Map<Integer, String> brands = new HashMap<>();
        Map<Integer, String> categories = new HashMap<>();

        try (Connection connection = DriverManager.getConnection(url, user, password);
             PreparedStatement statement = connection.prepareStatement(sql)) {

            ResultSet resultSet = statement.executeQuery();

            while (resultSet.next()) {
                products.add(mapRow(resultSet));
                brands.put(
                        resultSet.getInt("brand_id"),
                        resultSet.getString("brand_name")
                );
                categories.put(
                        resultSet.getInt("category_id"),
                        resultSet.getString("category_name")
                );
            }

        } catch (SQLException e) {
            throw new DataAccessException("Could not load products", e);
        }
        return new MobileProductsResponse(products,brands,categories);
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
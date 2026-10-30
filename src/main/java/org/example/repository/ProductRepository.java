package org.example.repository;

import org.example.dto.MobileProductsResponse;
import org.example.dto.PatchProductRequest;
import org.example.dto.ProductWithBrandAndCategoryRow;
import org.example.dto.ProductWriteRequest;
import org.example.exception.DataAccessException;
import org.example.model.Product;

import java.sql.*;
import java.util.*;

public class ProductRepository {
    private final String url;
    private final String user;
    private final String password;

    public ProductRepository(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

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

    public List<Product> readProduct(int limit, long offset, Integer brandId, Integer categoryId) {
        List<String> conditions = new ArrayList<>();
        List<Object> parameters = new ArrayList<>();

        // doar filtrele primite intră în WHERE; valorile merg tot prin "?", niciodată lipite în SQL
        if (brandId != null) {
            conditions.add("brand_id = ?");
            parameters.add(brandId);
        }
        if (categoryId != null) {
            conditions.add("category_id = ?");
            parameters.add(categoryId);
        }

        String sql = "SELECT * FROM product";
        if (!conditions.isEmpty()) {
            sql = sql + " WHERE " + String.join(" AND ", conditions);
        }
        sql = sql + " ORDER BY id LIMIT ? OFFSET ?";
        parameters.add(limit);
        parameters.add(offset);

        List<Product> products = new ArrayList<>();

        try (Connection connection = DriverManager.getConnection(url, user, password);
             PreparedStatement statement = connection.prepareStatement(sql)) {

            // indexul din JDBC începe de la 1, în ordinea semnelor "?"
            for (int i = 0; i < parameters.size(); i++) {
                statement.setObject(i + 1, parameters.get(i));
            }

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

    public List<ProductWithBrandAndCategoryRow> getProductsWithBrandsAndCategories(int offset, int limit){
        String sql = "SELECT p.id, p.name, p.price, b.id AS brand_id, b.name AS brand_name, c.id AS category_id, c.name AS category_name " +
                "FROM product p " +
                "INNER JOIN brand b ON p.brand_id = b.id " +
                "INNER JOIN category c ON p.category_id = c.id " +
                "ORDER BY p.id " +
                "LIMIT ? " +
                "OFFSET ?";
        List<ProductWithBrandAndCategoryRow> rows = new ArrayList<>();

        try (Connection connection = DriverManager.getConnection(url, user, password);
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, limit);
            statement.setInt(2, offset);

            ResultSet resultSet = statement.executeQuery();

            while (resultSet.next()) {
                rows.add(new ProductWithBrandAndCategoryRow(
                        resultSet.getInt("id"),
                        resultSet.getString("name"),
                        resultSet.getBigDecimal("price"),
                        resultSet.getInt("brand_id"),
                        resultSet.getString("brand_name"),
                        resultSet.getInt("category_id"),
                        resultSet.getString("category_name")
                ));
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not load products", e);
        }
        return rows;
    }

    public long countProducts(){
        String sql = "SELECT COUNT(*) AS total FROM product";

        try (Connection connection = DriverManager.getConnection(url, user, password);
             PreparedStatement statement = connection.prepareStatement(sql)) {
             ResultSet resultSet = statement.executeQuery();

            while (!resultSet.next()) {
                throw new SQLException("Product count query returned no row");
            }

            return resultSet.getLong("total");

        } catch (SQLException e) {
            throw new DataAccessException("Could not count products", e);
        }

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
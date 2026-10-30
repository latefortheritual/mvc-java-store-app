package org.example.repository;

import org.example.exception.DataAccessException;
import org.example.model.Product;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static org.mockito.Mockito.mock;

public class TestProductRepository {

    private static final String URL = "jdbc:postgresql://localhost/test";

    private final Connection connection = mock(Connection.class);
    private final PreparedStatement statement = mock(PreparedStatement.class);
    private final ResultSet resultSet = mock(ResultSet.class);
    private final ProductRepository productRepository = new ProductRepository(URL, "user", "password");

    // DriverManager.getConnection e static; îl înlocuim ca testul să nu aibă nevoie de baza de date
    private MockedStatic<DriverManager> driverManager;

    @BeforeEach
    void setUp() throws SQLException {
        driverManager = Mockito.mockStatic(DriverManager.class);
        driverManager.when(() -> DriverManager.getConnection(URL, "user", "password")).thenReturn(connection);
        Mockito.when(connection.prepareStatement(Mockito.anyString())).thenReturn(statement);
        Mockito.when(statement.executeQuery()).thenReturn(resultSet);
    }

    @AfterEach
    void tearDown() {
        driverManager.close();
    }

    @Test
    void without_filters_only_pagination_is_applied() throws SQLException {

        //Act
        productRepository.readProduct(10, 20L, null, null);

        //Assert
        Mockito.verify(connection).prepareStatement("SELECT * FROM product ORDER BY id LIMIT ? OFFSET ?");
        Mockito.verify(statement).setObject(1, 10);
        Mockito.verify(statement).setObject(2, 20L);
        Mockito.verify(statement, Mockito.times(2)).setObject(Mockito.anyInt(), Mockito.any());
    }

    @Test
    void brand_filter_is_added_before_pagination() throws SQLException {

        //Act
        productRepository.readProduct(10, 20L, 3, null);

        //Assert
        Mockito.verify(connection).prepareStatement("SELECT * FROM product WHERE brand_id = ? ORDER BY id LIMIT ? OFFSET ?");
        Mockito.verify(statement).setObject(1, 3);
        Mockito.verify(statement).setObject(2, 10);
        Mockito.verify(statement).setObject(3, 20L);
    }

    @Test
    void category_filter_is_added_before_pagination() throws SQLException {

        //Act
        productRepository.readProduct(10, 20L, null, 5);

        //Assert
        Mockito.verify(connection).prepareStatement("SELECT * FROM product WHERE category_id = ? ORDER BY id LIMIT ? OFFSET ?");
        Mockito.verify(statement).setObject(1, 5);
        Mockito.verify(statement).setObject(2, 10);
        Mockito.verify(statement).setObject(3, 20L);
    }

    @Test
    void both_filters_are_combined_with_and() throws SQLException {

        //Act
        productRepository.readProduct(10, 20L, 3, 5);

        //Assert
        Mockito.verify(connection).prepareStatement(
                "SELECT * FROM product WHERE brand_id = ? AND category_id = ? ORDER BY id LIMIT ? OFFSET ?");
        InOrder inOrder = Mockito.inOrder(statement);
        inOrder.verify(statement).setObject(1, 3);
        inOrder.verify(statement).setObject(2, 5);
        inOrder.verify(statement).setObject(3, 10);
        inOrder.verify(statement).setObject(4, 20L);
    }

    @Test
    void every_row_becomes_a_product() throws SQLException {

        //Arrange
        Mockito.when(resultSet.next()).thenReturn(true, true, false);
        Mockito.when(resultSet.getInt("id")).thenReturn(1, 2);
        Mockito.when(resultSet.getString("name")).thenReturn("Product 1", "Product 2");
        Mockito.when(resultSet.getInt("brand_id")).thenReturn(3, 3);
        Mockito.when(resultSet.getInt("category_id")).thenReturn(5, 6);
        Mockito.when(resultSet.getBigDecimal("price")).thenReturn(new BigDecimal("305.95"), new BigDecimal("10.00"));

        //Act
        List<Product> products = productRepository.readProduct(10, 0L, 3, null);

        //Assert
        Assertions.assertEquals(2, products.size());
        Assertions.assertEquals(1, products.get(0).getId());
        Assertions.assertEquals("Product 2", products.get(1).getName());
        Assertions.assertEquals(6, products.get(1).getCategory_id());
    }

    @Test
    void no_matching_rows_give_an_empty_list() throws SQLException {

        //Arrange
        Mockito.when(resultSet.next()).thenReturn(false);

        //Act
        List<Product> products = productRepository.readProduct(10, 0L, 999, null);

        //Assert
        Assertions.assertTrue(products.isEmpty());
    }

    @Test
    void sql_errors_become_data_access_exceptions() throws SQLException {

        //Arrange
        SQLException cause = new SQLException("Database connection failed");
        Mockito.when(statement.executeQuery()).thenThrow(cause);

        //Act
        DataAccessException exception = Assertions.assertThrows(DataAccessException.class,
                () -> productRepository.readProduct(10, 0L, null, null));

        //Assert
        Assertions.assertEquals("Could not load products", exception.getMessage());
        Assertions.assertSame(cause, exception.getCause());
    }
}

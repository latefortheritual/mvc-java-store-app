package org.example.exception;

import java.sql.SQLException;

public class DataAccessException extends RuntimeException {
    public DataAccessException(String message, SQLException e) {

      super(message, e);
    }
}

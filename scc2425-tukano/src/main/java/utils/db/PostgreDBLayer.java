package utils.db;

import tukano.api.Result;
import java.lang.reflect.Field;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class PostgreDBLayer implements DBLayer {

    //CONSOLE COMMAND AFTER FAILED ATTEMPT: psql -h c-projcluster.5slxk7ap6q23ri.postgres.cosmos.azure.com -p 5432 -U citus -d sccprojdb
    private static final String DB_HOSTNAME = "c-projcluster.5slxk7ap6q23ri.postgres.cosmos.azure.com";
    private static final String DB_NAME = "sccprojdb";
    private static final String DB_USERNAME = "citus";
    private static final String DB_PASSWORD = "Scc_secret";
    //private static final String DB_URL = String.format("jdbc:postgresql://%s:5432/%s?user=%s@%s&password=%s&ssl=true",DB_HOSTNAME, DB_NAME, DB_USERNAME, DB_HOSTNAME, DB_PASSWORD);
    private static final String DB_URL = "jdbc:postgresql://c-projcluster.5slxk7ap6q23ri.postgres.cosmos.azure.com:5432/sccprojdb?user=citus&password=Scc_secret&sslmode=require";
    private static PostgreDBLayer instance;
    private Connection connection;
    private static final Logger log = Logger.getLogger(PostgreDBLayer.class.getName());

    private PostgreDBLayer() {
        try {
            // Explicitly load the PostgreSQL driver
            Class.forName("org.postgresql.Driver");
            connection = DriverManager.getConnection(DB_URL);
        } catch (ClassNotFoundException e) {
            System.err.println("PostgreSQL Driver not found. Ensure it's included in your dependencies.");
            e.printStackTrace();
        } catch (SQLException e) {
            System.err.println("Failed to connect to the database. Check your connection details.");
            e.printStackTrace();
        }
    }

    public static synchronized PostgreDBLayer getInstance() {
        if (instance == null) {
            instance = new PostgreDBLayer();
        }
        return instance;
    }

    @Override
    public <T> Result<T> getOne(String id, Class<T> clazz, String table) {
        String query = "SELECT * FROM " + table + " WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, id);
            ResultSet resultSet = statement.executeQuery();
            if (resultSet.next()) {
                T result = mapResultSetToEntity(resultSet, clazz);
                return Result.ok(result);
            }
            return Result.error(Result.ErrorCode.NOT_FOUND);
        } catch (SQLException e) {
            e.printStackTrace();
            return Result.error(Result.ErrorCode.INTERNAL_ERROR);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public <T> Result<T> insertOne(T obj, String table) {
        try {
            Field[] fields = obj.getClass().getDeclaredFields();
            StringBuilder queryBuilder = new StringBuilder("INSERT INTO " + table + " (");
            StringBuilder valuesBuilder = new StringBuilder(" VALUES (");

            for (int i = 0; i < fields.length; i++) {
                fields[i].setAccessible(true);
                queryBuilder.append(fields[i].getName());
                valuesBuilder.append("?");
                if (i < fields.length - 1) {
                    queryBuilder.append(", ");
                    valuesBuilder.append(", ");
                }
            }

            queryBuilder.append(")");
            valuesBuilder.append(")");
            String insertQuery = queryBuilder.append(valuesBuilder).toString();

            try (PreparedStatement statement = connection.prepareStatement(insertQuery)) {
                setPreparedStatementFields(statement, fields, obj);
                statement.executeUpdate();
                return Result.ok(obj);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error(Result.ErrorCode.INTERNAL_ERROR);
        }
    }

    @Override
    public <T> Result<T> updateOne(T obj, String table) {
        try {
            Field[] fields = obj.getClass().getDeclaredFields();
            StringBuilder updateQuery = new StringBuilder("UPDATE " + table + " SET ");
            String idField = "id";

            for (int i = 0; i < fields.length; i++) {
                fields[i].setAccessible(true);
                if (fields[i].getName().equalsIgnoreCase(idField)) continue;
                updateQuery.append(fields[i].getName()).append(" = ?");
                if (i < fields.length - 1) updateQuery.append(", ");
            }
            updateQuery.append(" WHERE id = ?");

            try (PreparedStatement statement = connection.prepareStatement(updateQuery.toString())) {
                setPreparedStatementFields(statement, fields, obj);
                statement.setObject(fields.length, getIdFromEntity(obj));
                statement.executeUpdate();
                return Result.ok(obj);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error(Result.ErrorCode.INTERNAL_ERROR);
        }
    }

    @Override
    public <T> Result<T> deleteOne(T obj, String table) {
        String deleteQuery = "DELETE FROM " + table + " WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(deleteQuery)) {
            statement.setObject(1, getIdFromEntity(obj));
            statement.executeUpdate();
            return Result.ok();
        } catch (SQLException e) {
            e.printStackTrace();
            return Result.error(Result.ErrorCode.INTERNAL_ERROR);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public <T> Result<List<T>> query(Class<T> clazz, String queryStr, String table) {
        List<T> results = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(queryStr)) {
            ResultSet resultSet = statement.executeQuery();

            // Check if we're expecting simple String results
            if (clazz.equals(String.class)) {
                while (resultSet.next()) {
                    @SuppressWarnings("unchecked")
                    T result = (T) resultSet.getString(1);
                    results.add(result);
                }
            } else {
                // For other objects, map normally
                while (resultSet.next()) {
                    T result = mapResultSetToEntity(resultSet, clazz);
                    results.add(result);
                }
            }
            return Result.ok(results);
        } catch (SQLException | ReflectiveOperationException e) {
            e.printStackTrace();
            return Result.error(Result.ErrorCode.INTERNAL_ERROR);
        }
    }

    @Override
    public Result<Void> executeUpdate(String queryStr, String container) {
        try (PreparedStatement statement = connection.prepareStatement(queryStr)) {
            int rowsAffected = statement.executeUpdate();
            if (rowsAffected >= 0) {
                return Result.ok();
            } else {
                return Result.error(Result.ErrorCode.INTERNAL_ERROR);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return Result.error(Result.ErrorCode.INTERNAL_ERROR);
        }
    }


    private <T> T mapResultSetToEntity(ResultSet resultSet, Class<T> clazz) throws SQLException, ReflectiveOperationException {
        T obj = clazz.getDeclaredConstructor().newInstance();
        for (Field field : clazz.getDeclaredFields()) {
            field.setAccessible(true);
            Object value = resultSet.getObject(field.getName());
            field.set(obj, value);
        }
        return obj;
    }

    private <T> void setPreparedStatementFields(PreparedStatement statement, Field[] fields, T obj) throws SQLException, IllegalAccessException {
        for (int i = 0; i < fields.length; i++) {
            fields[i].setAccessible(true);
            statement.setObject(i + 1, fields[i].get(obj));
        }
    }

    private <T> Object getIdFromEntity(T obj) throws IllegalAccessException {
        for (Field field : obj.getClass().getDeclaredFields()) {
            if (field.getName().equalsIgnoreCase("id")) {
                field.setAccessible(true);
                return field.get(obj);
            }
        }
        throw new IllegalArgumentException("Object does not have an 'id' field");
    }
}

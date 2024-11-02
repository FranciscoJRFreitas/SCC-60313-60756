package utils.db;

import tukano.api.Result;

import java.util.List;

public interface DBLayer {
    <T> Result<T> getOne(String id, Class<T> clazz, String container);
    <T> Result<T> insertOne(T obj, String container);
    <T> Result<T> updateOne(T obj, String container);
    <T> Result<T> deleteOne(T obj, String container);
    <T> Result<List<T>> query(Class<T> clazz, String queryStr, String container);

    Result<Void> executeUpdate(String queryStr, String container);
}

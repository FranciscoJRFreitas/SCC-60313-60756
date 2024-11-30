package utils.db;

import tukano.api.Result;
import utils.Hibernate;

import java.sql.*;
import java.util.List;
import java.util.logging.Logger;

public class PostgreDBLayer implements DBLayer {

    //CONSOLE COMMAND AFTER FAILED ATTEMPT: psql -h c-projcluster.5slxk7ap6q23ri.postgres.cosmos.azure.com -p 5432 -U citus -d sccprojdb
    private static PostgreDBLayer instance;
    private static final Logger log = Logger.getLogger(PostgreDBLayer.class.getName());

    private PostgreDBLayer() {
    }

    public static synchronized PostgreDBLayer getInstance() {
        if (instance == null) {
            instance = new PostgreDBLayer();
        }
        return instance;
    }

    @Override
    public <T> Result<T> getOne(String id, Class<T> clazz, String table) {
        return Hibernate.getInstance().getOne(id, clazz);
    }

    @Override
    public <T> Result<T> insertOne(T obj, String table) {
        return Result.errorOrValue(Hibernate.getInstance().persistOne(obj), obj);
    }

    @Override
    public <T> Result<T> updateOne(T obj, String table) {
        return Hibernate.getInstance().updateOne(obj);
    }

    @Override
    public <T> Result<T> deleteOne(T obj, String table) {
        return Hibernate.getInstance().deleteOne(obj);
    }

    @Override
    public <T> Result<List<T>> query(Class<T> clazz, String queryStr, String table) {
        return Hibernate.getInstance().sql(queryStr, clazz);
    }

    @Override
    public Result<Void> executeUpdate(String queryStr, String container) {
        return Hibernate.getInstance().execute(session -> {
            session.createNativeQuery(queryStr).executeUpdate();
        });
    }
}

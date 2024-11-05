package utils.db;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;

import com.azure.cosmos.ConsistencyLevel;
import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;

import tukano.api.Result;
import tukano.api.Result.ErrorCode;
import tukano.impl.rest.utils.CustomLoggingFilter;

//Change the containers
public class CosmosDBLayer implements DBLayer {
    private static final String CONNECTION_URL = System.getenv("CONNECTION_URL");
    private static final String DB_KEY = System.getenv("DB_KEY");
    private static final String DB_NAME = System.getenv("DB_NAME");
    private static Logger Log = Logger.getLogger(CosmosDBLayer.class.getName());
    private static CosmosDBLayer instance;

    public static synchronized CosmosDBLayer getInstance() {
        if( instance != null)
            return instance;

        CosmosClient client = new CosmosClientBuilder()
                .endpoint(CONNECTION_URL)
                .key(DB_KEY)
                //.directMode()
                //.gatewayMode()
                .directMode()
                .consistencyLevel(ConsistencyLevel.SESSION)
                .connectionSharingAcrossClientsEnabled(true)
                .contentResponseOnWriteEnabled(true)
                .buildClient();
        instance = new CosmosDBLayer( client);
        return instance;
    }

    private final CosmosClient client;
    private CosmosDatabase db;


    public CosmosDBLayer(CosmosClient client) {
        this.client = client;
    }


    private synchronized void init() {
        if (db == null) {
            Log.info("Initializing Cosmos DB database.");
            db = client.getDatabase(DB_NAME); // Check the actual database name
        }
    }
    public void close() {
        client.close();
    }

    @Override
    public <T> Result<T> getOne(String id, Class<T> clazz, String container) {
        Log.info(() -> String.format("Getting item with id: %s", id));
        return tryCatch(() -> {
            CosmosContainer dynamicContainer = db.getContainer(container);
            return dynamicContainer.readItem(id, new PartitionKey(id), clazz).getItem();
        });
    }

    @Override
    public <T> Result<T> deleteOne(T obj, String container) {
        Log.info(() -> String.format("Deleting item: %s from container: %s", obj.toString(), container));
        return tryCatch(() -> {
            CosmosContainer dynamicContainer = db.getContainer(container);
            dynamicContainer.deleteItem(obj, new CosmosItemRequestOptions());
            return null;
        });
    }

    @Override
    public <T> Result<T> updateOne(T obj, String container) {
        Log.info(() -> String.format("Updating item: %s", obj.toString()));
        return tryCatch(() -> {
            CosmosContainer dynamicContainer = db.getContainer(container);
            return dynamicContainer.upsertItem(obj).getItem();
        });
    }

    @Override
    public <T> Result<T> insertOne(T obj, String container) {
        Log.info(() -> String.format("Inserting item: %s", obj.toString()));
        return tryCatch(() -> {
            CosmosContainer dynamicContainer = db.getContainer(container);
            return dynamicContainer.createItem(obj).getItem();
        });
    }

    @Override
    public <T> Result<List<T>> query(Class<T> clazz, String queryStr, String container) {
        return tryCatch(() -> {
            CosmosContainer dynamicContainer = db.getContainer(container);
            var res = dynamicContainer.queryItems(queryStr, new CosmosQueryRequestOptions(), clazz);
            return res.stream().toList();
        });
    }

    public <T, R> Result<List<R>> queryAndMapResults(Class<T> clazz, String queryStr, String container, Function<T, R> mapper) {
        return tryCatch(() -> {
            CosmosContainer dynamicContainer = db.getContainer(container);
            var queryResult = dynamicContainer.queryItems(queryStr, new CosmosQueryRequestOptions(), clazz);

            // Map the query results using the mapper function and collect into a list
            return queryResult.stream()
                    .map(mapper)
                    .toList();
        });
    }


    @Override
    public Result<Void> executeUpdate(String queryStr, String container) {
        return tryCatch(() -> {
            CosmosContainer dynamicContainer = db.getContainer(container);

            var items = dynamicContainer.queryItems(queryStr, new CosmosQueryRequestOptions(), Object.class);

            items.forEach(item -> {
                try {
                    dynamicContainer.deleteItem(item, new CosmosItemRequestOptions());
                } catch (CosmosException ce) {
                    Log.warning(() -> String.format("Failed to delete item: %s - CosmosException: %s", item, ce.getMessage()));
                }
            });

            return null;
        });
    }


    <T> Result<T> tryCatch(Supplier<T> supplierFunc) {
        try {
            init();
            return Result.ok(supplierFunc.get());
        } catch (CosmosException ce) {
            Log.severe(() -> String.format("CosmosException: %s - ActivityId: %s", ce.getMessage(), ce.getActivityId()));
            return Result.error(errorCodeFromStatus(ce.getStatusCode()));
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error(ErrorCode.INTERNAL_ERROR);
        }
    }


    static Result.ErrorCode errorCodeFromStatus( int status ) {
        return switch( status ) {
            case 200 -> ErrorCode.OK;
            case 404 -> ErrorCode.NOT_FOUND;
            case 409 -> ErrorCode.CONFLICT;
            default -> ErrorCode.INTERNAL_ERROR;
        };
    }
}
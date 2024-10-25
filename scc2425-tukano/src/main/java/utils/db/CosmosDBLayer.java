package utils.db;

import java.util.List;
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


public class CosmosDBLayer {
    private static final String CONNECTION_URL = "https://cosmos6031360756.documents.azure.com:443/";
    private static final String DB_KEY = "qmSP9N9KECvGGO2WCbsEs9Ay44E3ISkbWmbckbP50M7loCqIsQU5M2ybvoK2PaJ7o4z21G53UTqkACDbfCXG3A==";
    private static final String DB_NAME = "cosmosdb6031360756";
    private static final String CONTAINER = "users";
    private static Logger Log = Logger.getLogger(CosmosDBLayer.class.getName());

    private static CosmosDBLayer instance;

    public static synchronized CosmosDBLayer getInstance() {
        if( instance != null)
            return instance;

        CosmosClient client = new CosmosClientBuilder()
                .endpoint(CONNECTION_URL)
                .key(DB_KEY)
                //.directMode()
                .gatewayMode()
                // replace by .directMode() for better performance
                .consistencyLevel(ConsistencyLevel.SESSION)
                .connectionSharingAcrossClientsEnabled(true)
                .contentResponseOnWriteEnabled(true)
                .buildClient();
        instance = new CosmosDBLayer( client);
        return instance;

    }

    private CosmosClient client;
    private CosmosDatabase db;
    private CosmosContainer container;


    public CosmosDBLayer(CosmosClient client) {
        this.client = client;
    }

    private synchronized void init() {
        if (db == null || container == null) {
            Log.info("Initializing Cosmos DB database and container.");
            db = client.getDatabase(DB_NAME); // Check the actual database name
            container = db.getContainer(CONTAINER); // Check the container name
            Log.info(String.format("Database: %s, Container: %s initialized", DB_NAME, CONTAINER));
        }
    }

    public void close() {
        client.close();
    }

    public <T> Result<T> getOne(String id, Class<T> clazz) {
        Log.info(() -> String.format("Getting item with id: %s", id));
        return tryCatch(() -> container.readItem(id, new PartitionKey(id), clazz).getItem());
    }

    public <T> Result<T> deleteOne(T obj) {
        return (Result<T>) tryCatch( () -> container.deleteItem(obj, new CosmosItemRequestOptions()).getItem());
    }

    public <T> Result<T> updateOne(T obj) {
        return tryCatch( () -> container.upsertItem(obj).getItem());
    }

    public <T> Result<T> insertOne(T obj) {
        Log.info(() -> String.format("Inserting item: %s", obj.toString()));
        return tryCatch(() -> container.createItem(obj).getItem());
    }

    public <T> Result<List<T>> query(Class<T> clazz, String queryStr) {
        return tryCatch(() -> {
            var res = container.queryItems(queryStr, new CosmosQueryRequestOptions(), clazz);
            return res.stream().toList();
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
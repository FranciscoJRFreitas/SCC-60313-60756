package utils.db;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobContainerClientBuilder;
import com.azure.storage.blob.models.BlobItem;
import tukano.api.Result;
import tukano.api.Result.ErrorCode;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.logging.Logger;

public class AzureBlobStorage {

    private static final String BLOBS_CONTAINER_NAME = "shorts";
    private static final String STORAGE_CONNECTION_STRING = "DefaultEndpointsProtocol=https;AccountName=sccproj;AccountKey=Izr6E4pu7x9ly2tNHmnP/DbGZhrwSunWoppYSZ7PBcXrKqwcZD0W4DUl9rGs5YtmqMKmTNDNXx3W+ASt+roZZQ==;EndpointSuffix=core.windows.net";

    private final BlobContainerClient containerClient;
    private static final Logger Log = Logger.getLogger(AzureBlobStorage.class.getName());

    public AzureBlobStorage() {
        this.containerClient = new BlobContainerClientBuilder()
                .connectionString(STORAGE_CONNECTION_STRING)
                .containerName(BLOBS_CONTAINER_NAME)
                .buildClient();

        if (!containerClient.exists()) {
            containerClient.create();
        }
    }

    public Result<Void> uploadBlob(String blobId, byte[] data) {
        try {
            BlobClient blobClient = containerClient.getBlobClient(blobId);
            try (InputStream dataStream = new ByteArrayInputStream(data)) {
                blobClient.upload(dataStream, data.length, true);
            }
            return Result.ok();
        } catch (Exception e) {
            Log.severe("Failed to upload blob: " + e.getMessage());
            return Result.error(ErrorCode.INTERNAL_ERROR);
        }
    }

    public Result<byte[]> downloadBlob(String blobId) {
        try {
            BlobClient blobClient = containerClient.getBlobClient(blobId);
            if (blobClient.exists()) {
                return Result.ok(blobClient.downloadContent().toBytes());
            } else {
                return Result.error(ErrorCode.NOT_FOUND);
            }
        } catch (Exception e) {
            Log.severe("Failed to download blob: " + e.getMessage());
            return Result.error(ErrorCode.INTERNAL_ERROR);
        }
    }

    public Result<Void> deleteBlob(String blobId) {
        try {
            BlobClient blobClient = containerClient.getBlobClient(blobId);
            if (blobClient.exists()) {
                blobClient.delete();
            }
            return Result.ok();
        } catch (Exception e) {
            Log.severe("Failed to delete blob: " + e.getMessage());
            return Result.error(ErrorCode.INTERNAL_ERROR);
        }
    }

    public Result<Void> deleteAllBlobsInPath(String pathPrefix) {
        try {
            for (BlobItem blobItem : containerClient.listBlobs()) {
                if (blobItem.getName().startsWith(pathPrefix)) {
                    containerClient.getBlobClient(blobItem.getName()).delete();
                }
            }
            return Result.ok();
        } catch (Exception e) {
            Log.severe("Failed to delete blobs with prefix: " + e.getMessage());
            return Result.error(ErrorCode.INTERNAL_ERROR);
        }
    }
}

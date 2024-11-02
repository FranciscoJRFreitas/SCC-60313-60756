package utils.db;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobContainerClientBuilder;


public class AzureBlobStorage  {

    private static final String BLOBS_CONTAINER_NAME = "shorts";
    private static final  String STORAGE_CONNECTION_STRING = "DefaultEndpointsProtocol=https;AccountName=scc6031360756;AccountKey=SzxIKeLNMWiK4fdpSeIeuxY19OmvoAGcsJBClvxGqLhSZZ5ghvGMy3Nt3OWXvVvbdU/ImvIFkn6W+AStR/sk0Q==;EndpointSuffix=core.windows.net";


    private final BlobContainerClient containerClient;

    public AzureBlobStorage() {
        this.containerClient = new BlobContainerClientBuilder()
                .connectionString(STORAGE_CONNECTION_STRING)
                .containerName(BLOBS_CONTAINER_NAME)
                .buildClient();
    }


}
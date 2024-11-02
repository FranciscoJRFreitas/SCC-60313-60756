package tukano.impl;

import static java.lang.String.format;
import static tukano.api.Result.error;
import static tukano.api.Result.ErrorCode.FORBIDDEN;

import java.util.logging.Logger;

import tukano.api.Blobs;
import tukano.api.Result;
import utils.Hash;
import utils.Hex;

import utils.db.AzureBlobStorage;

public class JavaBlobs implements Blobs {

	private static Blobs instance;
	private static final Logger Log = Logger.getLogger(JavaBlobs.class.getName());

	private final AzureBlobStorage storage;

	private JavaBlobs() {
		storage = new AzureBlobStorage();
	}

	synchronized public static Blobs getInstance() {
		if (instance == null)
			instance = new JavaBlobs();
		return instance;
	}

	@Override
	public Result<Void> upload(String blobId, byte[] bytes, String token) {
		Log.info(() -> format("upload : blobId = %s, sha256 = %s, token = %s\n", blobId, Hex.of(Hash.sha256(bytes)), token));

		if (!validBlobId(blobId, token))
			return error(FORBIDDEN);

		return storage.uploadBlob(blobId, bytes);
	}

	@Override
	public Result<byte[]> download(String blobId, String token) {
		Log.info(() -> format("download : blobId = %s, token=%s\n", blobId, token));

		if (!validBlobId(blobId, token))
			return error(FORBIDDEN);

		return storage.downloadBlob(blobId);
	}

	@Override
	public Result<Void> delete(String blobId, String token) {
		Log.info(() -> format("delete : blobId = %s, token=%s\n", blobId, token));

		if (!validBlobId(blobId, token))
			return error(FORBIDDEN);

		return storage.deleteBlob(blobId);
	}

	@Override
	public Result<Void> deleteAllBlobs(String userId, String token) {
		Log.info(() -> format("deleteAllBlobs : userId = %s, token=%s\n", userId, token));

		if (!Token.isValid(token, userId))
			return error(FORBIDDEN);

		return storage.deleteAllBlobsInPath(userId);
	}

	private boolean validBlobId(String blobId, String token) {
		return Token.isValid(token, blobId);
	}
}


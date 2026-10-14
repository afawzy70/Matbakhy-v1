package com.matbakhy.app.providers;

import com.matbakhy.catalog.api.ObjectStorage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.OptionalLong;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/** S3-API adapter (local S3 emulator in LOCAL/CI; the production provider is open decision O07). */
class S3ObjectStorage implements ObjectStorage {

    private final StorageProperties props;
    private final S3Client client;
    private final S3Presigner presigner;

    S3ObjectStorage(StorageProperties props) {
        this.props = props;
        var credentials = StaticCredentialsProvider.create(AwsBasicCredentials.create(props.accessKey(), props.secretKey()));
        var pathStyle = S3Configuration.builder().pathStyleAccessEnabled(true).build();
        this.client = S3Client.builder().httpClient(UrlConnectionHttpClient.create()).endpointOverride(props.endpoint())
                .region(Region.of(props.region())).credentialsProvider(credentials).serviceConfiguration(pathStyle)
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED).build();
        this.presigner = S3Presigner.builder().endpointOverride(props.signingEndpoint()).region(Region.of(props.region()))
                .credentialsProvider(credentials).serviceConfiguration(pathStyle).build();
    }

    private String bucket(Bucket bucket) {
        return bucket == Bucket.PRIVATE ? props.privateBucket() : props.publicBucket();
    }

    @Override
    public URI presignUpload(String key, String contentType, long bytes, Duration ttl) {
        return URI.create(presigner.presignPutObject(r -> r.signatureDuration(ttl).putObjectRequest(p -> p
                .bucket(props.privateBucket()).key(key).contentType(contentType).contentLength(bytes))).url().toString());
    }

    @Override
    public URI presignRead(String key, Duration ttl) {
        return URI.create(presigner.presignGetObject(r -> r.signatureDuration(ttl).getObjectRequest(g -> g
                .bucket(props.privateBucket()).key(key).responseContentDisposition("attachment"))).url().toString());
    }

    @Override
    public OptionalLong size(Bucket bucket, String key) {
        try {
            return OptionalLong.of(client.headObject(h -> h.bucket(bucket(bucket)).key(key)).contentLength());
        } catch (NoSuchKeyException e) {
            return OptionalLong.empty();
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return OptionalLong.empty();
            }
            throw e;
        }
    }

    @Override
    public byte[] read(Bucket bucket, String key, long maxBytes) {
        try (ResponseInputStream<GetObjectResponse> in = client.getObject(g -> g.bucket(bucket(bucket)).key(key))) {
            return readBounded(in, maxBytes);
        } catch (IOException e) {
            throw new IllegalStateException("object read failed", e);
        }
    }

    static byte[] readBounded(InputStream in, long maxBytes) throws IOException {
        byte[] data = in.readNBytes((int) Math.min(Integer.MAX_VALUE - 8, maxBytes + 1));
        if (data.length > maxBytes) {
            throw new IllegalStateException("object larger than " + maxBytes + " bytes");
        }
        return data;
    }

    @Override
    public void write(Bucket bucket, String key, byte[] content, String contentType) {
        client.putObject(p -> p.bucket(bucket(bucket)).key(key).contentType(contentType).contentLength((long) content.length),
                RequestBody.fromBytes(content));
    }

    @Override
    public void delete(Bucket bucket, String key) {
        client.deleteObject(d -> d.bucket(bucket(bucket)).key(key));
    }

    @Override
    public URI publicUrl(String key) {
        URI base = props.hasPublicBaseUrl() ? props.publicBaseUrl()
                : URI.create(props.signingEndpoint().toString().replaceAll("/$", "") + "/" + props.publicBucket() + "/");
        return base.resolve(key);
    }
}

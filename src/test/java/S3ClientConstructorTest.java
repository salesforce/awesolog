import com.salesforce.mce.awesolog.aws.S3ClientConstructor;
import org.junit.Test;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetUrlRequest;

import java.net.URL;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.MatcherAssert.assertThat;

public class S3ClientConstructorTest {

    @Test
    public void testNoParameters() {
        S3Client s3Client = new S3ClientConstructor(
                null,
                null,
                null,
                null,
                null,
                null
        ).construct();
        assert(s3Client != null);
        GetUrlRequest.Builder getUrlRequestBuilder = GetUrlRequest.builder();
        GetUrlRequest getUrlRequest = getUrlRequestBuilder
                .bucket("bucket")
                .key("key")
                .build();
        URL urlRequest = s3Client.utilities().getUrl(getUrlRequest);
        String host = urlRequest.getHost();
        assertTrue(host.startsWith("s3"));
        assertTrue(host.endsWith("amazonaws.com"));
        assertEquals("/bucket/key", urlRequest.getPath());
    }

    @Test
    public void testRegionOnly() {
        S3Client s3Client = new S3ClientConstructor(
                null,
                null,
                null,
                null,
                "us-west-2",
                null
        ).construct();
        assert(s3Client != null);
        GetUrlRequest.Builder getUrlRequestBuilder = GetUrlRequest.builder();
        GetUrlRequest getUrlRequest = getUrlRequestBuilder
                .bucket("bucket")
                .key("key")
                .build();
        URL urlRequest = s3Client.utilities().getUrl(getUrlRequest);
        assertEquals("s3.us-west-2.amazonaws.com", urlRequest.getHost());
        assertEquals("/bucket/key", urlRequest.getPath());
    }

    @Test
    public void testEndpointParameterConstructor() {
        S3Client s3Client = new S3ClientConstructor(
                "user",
                "password",
                null,
                null,
                null,
                "http://minio:9000"
        ).construct();
        assert(s3Client != null);
        GetUrlRequest.Builder getUrlRequestBuilder = GetUrlRequest.builder();
        GetUrlRequest getUrlRequest = getUrlRequestBuilder
                .bucket("bucket")
                .key("key")
                .build();
        URL urlRequest = s3Client.utilities().getUrl(getUrlRequest);
        assertEquals("minio", urlRequest.getHost());
        assertEquals(9000, urlRequest.getPort());
        assertEquals("/bucket/key", urlRequest.getPath());
    }
}

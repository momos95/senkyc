package sn.senkyc.api.platform.ai;

import java.net.http.HttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AiServiceProperties.class)
class AiServiceConfig {

    @Bean
    AiServiceClient aiServiceClient(AiServiceProperties properties) {
        // HTTP/1.1 explicite : évite la tentative d'upgrade h2c du client JDK vers uvicorn.
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());

        RestClient restClient = RestClient.builder()
                .baseUrl(properties.baseUrl().toString())
                .requestFactory(requestFactory)
                .requestInterceptor(new AiServiceRequestInterceptor(properties.internalToken()))
                .build();
        return new RestAiServiceClient(restClient);
    }

    /** Contributeur de santé « aiService », inclus dans le groupe readiness. */
    @Bean
    AiServiceHealthIndicator aiServiceHealthIndicator(AiServiceClient client) {
        return new AiServiceHealthIndicator(client);
    }
}

package wf.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportResource;
import org.springframework.context.annotation.Scope;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.security.web.reactive.result.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.web.reactive.server.WebTestClientConfigurer;
import org.springframework.web.reactive.result.method.HandlerMethodArgumentResolver;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.server.adapter.WebHttpHandlerBuilder;
import reactor.core.publisher.Mono;
import wf.util.ConditionalOnMissingBean;

/**
 * Ref
 * <a href="https://stackoverflow.com/questions/50214030/accessing-the-websession-in-webfluxtest">
 * accessing-the-websession-in-webfluxtest</a> <br>
 * WebTestClientConfigurer enable @WebFluxTest do the following:<br>
 * webTestClient.mutateWith(sessionMutator(sessionBuilder().put("sessionKey",
 * "sessionValue").build()))
 */
@Configuration
@ImportResource("classpath:applicationContext.xml")
@Import({WebConfig.class, H2R2dbConfig.class, WsConfig.class})
public class TestContext implements WebTestClientConfigurer {

    static {
        reactor.util.Loggers.useSl4jLoggers();
    }

    @Bean("testAuthenticationPrincipalResolver")
    @ConditionalOnMissingBean(AuthenticationPrincipalArgumentResolver.class)
    public HandlerMethodArgumentResolver authenticationPrincipalArgumentResolver(BeanFactory beanFactory) {
        return new TestAuthenticationPrincipalResolver(beanFactory);
    }

    /**
     * ObjectMapper 是 ThreadSafe，但為了效能考量 ，還是定義為PROTOTYPE
     *
     * @return
     */
    @Bean
    @Scope(BeanDefinition.SCOPE_PROTOTYPE)
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
        mapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true);
        mapper.configure(JsonParser.Feature.ALLOW_COMMENTS, true);
        return mapper;
    }

    private static Map<String, Object> sessionMap;

    public TestContext() {
        TestContext.sessionMap = new ConcurrentHashMap<>();
    }

    private TestContext(final Map<String, Object> sessionMap) {
        TestContext.sessionMap = sessionMap;
    }

    public static TestContext sessionMutator(final Map<String, Object> sessionMap) {
        return new TestContext(sessionMap);
    }

    @Override
    public void afterConfigurerAdded(final WebTestClient.Builder builder,
            final WebHttpHandlerBuilder httpHandlerBuilder,
            final ClientHttpConnector connector) {
        final SessionMutatorFilter sessionMutatorFilter = new SessionMutatorFilter();
        httpHandlerBuilder.filters(filters -> filters.add(0, sessionMutatorFilter));
    }

    public static ImmutableMap.Builder<String, Object> sessionBuilder() {
        return new ImmutableMap.Builder<>();
    }

    private static class SessionMutatorFilter implements WebFilter {

        @Override
        public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain webFilterChain) {
            return exchange.getSession()
                    .doOnNext(webSession -> webSession.getAttributes().putAll(sessionMap))
                    .then(webFilterChain.filter(exchange));
        }
    }

}

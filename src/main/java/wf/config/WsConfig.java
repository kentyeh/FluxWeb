package wf.config;

import java.util.HashMap;
import java.util.Map;
import java.util.function.UnaryOperator;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Scope;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.web.reactive.HandlerAdapter;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;
import org.springframework.web.reactive.socket.server.WebSocketService;
import reactor.util.Logger;
import wf.model.WebSocketRedisListener;
import wf.spring.HandshakeFluxWebSocketService;
import wf.util.Loggers4j2;

/**
 *
 * @author kent
 */
@Configuration
@DependsOn("serverLogoutSuccessHandler")
@Import(RedisCacheConfig.class)
//@EnableScheduling
public class WsConfig {
    private static final Logger logger = Loggers4j2.getLogger(WsConfig.class);
    @Bean
    public HandlerAdapter wsHandlerAdapter() {
        return new WebSocketHandlerAdapter(webSocketService());
    }

    @Bean
    public HandlerMapping handlerMapping(WebSocketHandler webSocketHandler) {
        String path = "/chat";

        Map<String, WebSocketHandler> map = new HashMap<>();
        map.put("/ocs", webSocketHandler);
        map.put(path, webSocketHandler);

        return new SimpleUrlHandlerMapping(map, -1);
    }

    @Bean
    public WebSocketService webSocketService() {
        return new HandshakeFluxWebSocketService();
    }

    @Bean
    @Scope(BeanDefinition.SCOPE_PROTOTYPE)
    public <T> WebSocketRedisListener webSocketRedisListener(WebSocketSession session,
            ReactiveRedisMessageListenerContainer container, String topic, String jsessionId,
            RedisSerializationContext<String, T> serializationContext,
            ReactiveRedisTemplate<String, T> reactiveRedisTemplate,
            UnaryOperator<T> processor) {
        return new WebSocketRedisListener(session, container, topic, jsessionId,
                serializationContext, reactiveRedisTemplate, processor);
    }
    /*@Bean
     public WebSocketHandler webSocketHandler(EventUnicastService eventUnicastService) {
     return new FluxWebSocketHandler();
     }*/
}

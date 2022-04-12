package wf.spring;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.MultimapBuilder;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.function.UnaryOperator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.ReactiveSubscription;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.security.web.server.authentication.logout.ServerLogoutSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.web.reactive.socket.CloseStatus;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.server.WebSession;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.Logger;
import wf.model.WebSocketRedisListener;
import wf.util.Loggers4j2;

/**
 *
 * @author Kent Yeh
 */
@Component("serverLogoutSuccessHandler")
public class FluxWebSocketHandler implements WebSocketHandler, ServerLogoutSuccessHandler {

    private static final Logger logger = Loggers4j2.getLogger(FluxWebSocketHandler.class);
    public static final ListMultimap<String, WebSocketRedisListener> userListenser = MultimapBuilder.hashKeys().arrayListValues().build();

    private ApplicationContext context;

    private ReactiveRedisConnectionFactory connectionFactory;
    private ObjectMapper objectMapper;

    public static final String DEFAULT_LOGOUT_SUCCESS_URL = "/login?logout";
    private URI logoutSuccessUrl = URI.create(DEFAULT_LOGOUT_SUCCESS_URL);

    //訊息編解碼
    private final RedisSerializer<JsonNode> notifySerializer = new Jackson2JsonRedisSerializer<>(JsonNode.class);
    private final RedisSerializationContext<String, JsonNode> serializationContext
            = RedisSerializationContext.<String, JsonNode>newSerializationContext(RedisSerializer.string())
            .value(notifySerializer).build();

    @Autowired
    public void setContext(ApplicationContext context) {
        this.context = context;
    }

    @Autowired
    public void setObjectMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void setLogoutSuccessUrl(URI logoutSuccessUrl) {
        Assert.notNull(logoutSuccessUrl, "logoutSuccessUrl cannot be null");
        this.logoutSuccessUrl = logoutSuccessUrl;
    }

    @Autowired
    public void setLettuceConnFactory(ReactiveRedisConnectionFactory LettuceConnFactory) {
        this.connectionFactory = LettuceConnFactory;
    }

    @Override
    public Mono<Void> onLogoutSuccess(WebFilterExchange exchange, Authentication authentication) {
        String username = authentication.getPrincipal() == null
                ? exchange.getExchange().getPrincipal().map(p -> p.getName()).block()
                : User.class.isAssignableFrom(authentication.getPrincipal().getClass()) ? ((User) authentication.getPrincipal()).getUsername()
                        : Principal.class.isAssignableFrom(authentication.getPrincipal().getClass()) ? ((Principal) authentication.getPrincipal()).getName() : null;
        logger.info(":{}登出", username);
        exchange.getExchange().getSession().subscribe(ws -> {
            logger.info("JSESSIONID:{}", ws.getId());
            userListenser.removeAll(ws.getId()).forEach(wrl -> wrl.destroy());
        }, t -> logger.error("登出排除WS時錯誤:" + t.getMessage(), t)
        );
        ServerHttpResponse response = exchange.getExchange().getResponse();
        response.setStatusCode(HttpStatus.FOUND);
        response.getCookies().remove("JSESSIONID");
        response.getHeaders().setLocation(logoutSuccessUrl);
        return exchange.getExchange().getSession().flatMap(WebSession::invalidate);
    }

    /**
     * 為什麼session.handshakeInfo.principal永遠是空?<br>
     * 參考
     * <a href="https://github.com/spring-projects/spring-framework/blob/main/spring-webflux/src/main/java/org/springframework/web/reactive/socket/server/support/HandshakeWebSocketService.java#L285 ">HandshakeWebSocketService</a>，
     * Principal來自
     * ServerWebExchange，而主要實做的<a href="https://github.com/spring-projects/spring-framework/blob/main/spring-web/src/main/java/org/springframework/web/server/adapter/DefaultServerWebExchange.java#L210">DefaultServerWebExchange.princial</a>
     * 永遠回傳 Mono.empty(); ，所以無法從handshakeInfo取得Principal
     *
     * @param session
     * @return
     */
    @Override
    public Mono<Void> handle(WebSocketSession session) {
        Object jsessionId = session.getAttributes().get("JSESSIONID");
        Object sectximp = session.getAttributes().get(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        if (sectximp != null && jsessionId != null && !jsessionId.toString().trim().isEmpty()) {
            SecurityContext sectx = (SecurityContextImpl) sectximp;
            Authentication auth = sectx.getAuthentication();
            User user = auth == null ? null : ((User) auth.getPrincipal());
            if (user != null) {
                ReactiveRedisMessageListenerContainer container = context.getBean(ReactiveRedisMessageListenerContainer.class);
                ReactiveRedisTemplate<String, JsonNode> redisTemplate = new ReactiveRedisTemplate<>(connectionFactory, serializationContext);
                UnaryOperator<JsonNode> processor = notify -> notify;
                WebSocketRedisListener<JsonNode> wsrl = context.getBean(WebSocketRedisListener.class,
                        session, container, user.getUsername(), jsessionId, serializationContext, redisTemplate, processor);
                logger.info("put listener[{}] {}", jsessionId, userListenser.put(jsessionId.toString().trim(), wsrl));
                return session.receive().flatMap(webSocketMessage -> {
                    String payload = webSocketMessage.getPayloadAsText();
                    logger.debug("收到:{}", payload);
                    //convert payload to JsonNode
                    JsonNode notify = serializationContext.getValueSerializationPair().read(ByteBuffer.wrap(payload.getBytes(StandardCharsets.UTF_8)));
                    //wsrl.getSink().emitNext(notify, Sinks.EmitFailureHandler.FAIL_FAST);
                    wsrl.getReactiveRedisTemplate().convertAndSend(user.getUsername(), notify).subscribe();
                    return Mono.empty();
                }).doOnTerminate(() -> {
                    if (userListenser.get(jsessionId.toString()).remove(wsrl)) {
                        wsrl.destroy();
                        logger.info("移除監聽器");
                    } else {
                        logger.error("移除監聽器失敗");
                    }
                }).doFinally(signal -> {
                }).then(Mono.empty());
            } else {
                logger.debug("沒有User資料");
            }
        } else {
            logger.debug("沒有登錄資料");
        }
        return Mono.empty();
    }

    /**
     * 原本用來檢視過網參數用的，現已無用
     *
     * @param session
     * @return
     */
    public Mono<Void> handle2(WebSocketSession session) {
        Object jsessionId = session.getAttributes().get("JSESSIONID");
        Object sectximp = session.getAttributes().get(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        if (sectximp != null && jsessionId != null && !jsessionId.toString().trim().isEmpty()) {
            SecurityContext sectx = (SecurityContextImpl) sectximp;
            Authentication auth = sectx.getAuthentication();
            User user = auth == null ? null : ((User) auth.getPrincipal());
            if (user != null) {
                ReactiveRedisMessageListenerContainer container = new ReactiveRedisMessageListenerContainer(connectionFactory);
                //userListenser.put(jsessionId.toString().trim(), new WebSocketRedisListener(session, container));
                container.<String, JsonNode>receive(Lists.newArrayList(ChannelTopic.of(user.getUsername())),//以帳號訂閱通知
                        serializationContext.getKeySerializationPair(),
                        serializationContext.getValueSerializationPair())
                        .subscribeOn(Schedulers.boundedElastic())
                        .map(ReactiveSubscription.Message::getMessage)
                        .map(notify -> session.isOpen() ? session.send(Mono.just(session.textMessage(notify.toString()))) : Mono.empty())
                        .subscribe();
            }

        }
        return session.receive().flatMap(webSocketMessage -> {
            String payload = webSocketMessage.getPayloadAsText();
            logger.debug("收到:{}", payload);
            //假設接收到的是標準的Json字串以進行轉換
            try {
                //文字解碼
                JsonNode notify = serializationContext.getValueSerializationPair().read(ByteBuffer.wrap(payload.getBytes(StandardCharsets.UTF_8)));
                return session.send(Mono.just(session.textMessage(
                        StandardCharsets.UTF_8.decode(serializationContext.getValueSerializationPair().write(notify)).toString())));
            } catch (org.springframework.data.redis.serializer.SerializationException ex) {
                logger.error(ex.getClass().getName() + ":" + ex.getMessage());
                ObjectNode objectNode = objectMapper.createObjectNode();
                objectNode.put("error", ex.getMessage());
                JsonNode notify = serializationContext.getValueSerializationPair().read(ByteBuffer.wrap(objectNode.toString().getBytes(StandardCharsets.UTF_8)));
                return session.send(Mono.just(session.textMessage(
                        StandardCharsets.UTF_8.decode(serializationContext.getValueSerializationPair().write(notify)).toString())));
            }
        }).doOnTerminate(() -> {
            logger.info("善後");
            session.close(CloseStatus.NORMAL);
        }).then().doFinally(signal -> {
            /*userListenser.remove(sectximp, sectximp)
             session.getHandshakeInfo().getPrincipal().subscribe(p -> {
             clients.remove(session.getId());
             ReactiveRedisMessageListenerContainer container = wsidRedisContainer.get(session.getId());
             if (container != null) {
             container.destroyLater().subscribe();//取消訂閱通知
             }
             });*/
        });
    }

}

package wf.model;

import com.google.common.collect.Lists;
import java.nio.charset.StandardCharsets;
import java.util.function.UnaryOperator;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.redis.connection.ReactiveSubscription;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.web.reactive.socket.CloseStatus;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;
import reactor.util.Logger;
import wf.util.Loggers4j2;

/**
 *
 * @author Kent Yeh
 * @param <T> 要監聽與發佈到WebSocket的資料型態
 */
public class WebSocketRedisListener<T> implements InitializingBean, DisposableBean {

    private static final Logger logger = Loggers4j2.getLogger(WebSocketRedisListener.class);
    private final WebSocketSession ws;
    private final ReactiveRedisMessageListenerContainer listener;
    private final String topic;
    private final String jsessionId;
    private final RedisSerializationContext<String, T> serializationContext;
    private final Sinks.Many<T> sink;
    private final UnaryOperator<T> processor;
    private final ReactiveRedisTemplate<String, T> reactiveRedisTemplate;

    public WebSocketRedisListener(WebSocketSession ws, ReactiveRedisMessageListenerContainer listener, String topic,
            String jsessionId, RedisSerializationContext<String, T> serializationContext,
            ReactiveRedisTemplate<String, T> reactiveRedisTemplate) {
        this(ws, listener, topic, jsessionId, serializationContext, reactiveRedisTemplate, t -> t);
    }

    public WebSocketRedisListener(WebSocketSession ws, ReactiveRedisMessageListenerContainer listener, String topic,
            String jsessionId, RedisSerializationContext<String, T> serializationContext,
            ReactiveRedisTemplate<String, T> reactiveRedisTemplate, UnaryOperator<T> processor) {
        this.ws = ws;
        this.listener = listener;
        this.topic = topic;
        this.jsessionId = jsessionId;
        this.serializationContext = serializationContext;
        this.reactiveRedisTemplate = reactiveRedisTemplate;
        this.sink = Sinks.many().unicast().onBackpressureBuffer();
        this.processor = processor;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        listener.<String, T>receive(Lists.newArrayList(ChannelTopic.of(topic)),//以帳號訂閱通知
                serializationContext.getKeySerializationPair(),
                serializationContext.getValueSerializationPair())
                .subscribeOn(Schedulers.boundedElastic())
                .map(ReactiveSubscription.Message::getMessage)
                .map(notify -> {
                    sink.emitNext(notify, Sinks.EmitFailureHandler.FAIL_FAST);
                    return notify;
                })
                .doOnError(t -> logger.error("訂閱異常:{}",t.getMessage()))
                .onErrorResume(t-> Mono.empty())
                .doFinally(signal -> listener.destroyLater().subscribe())
                .subscribe(notify -> {
                    logger.info("發射{}到Sink", notify);
                });
        sink.asFlux().subscribe(notify -> {
            if (ws.isOpen()) {
                reactiveRedisTemplate.hasKey("spring:session:sessions:" + jsessionId)
                        .flatMap(b -> {
                            if (b) {
                                logger.info("轉發:{}", notify);
                                return ws.send(Mono.just(ws.textMessage(
                                        StandardCharsets.UTF_8.decode(
                                                serializationContext.getValueSerializationPair().write(processor.apply(notify))
                                        ).toString()
                                )));
                            } else {
                                destroy();
                                return Mono.empty();
                            }
                        }).subscribe();
            } else {
                logger.info("Web Socket 並未連線");
            }
        });
    }

    public WebSocketSession getWs() {
        return ws;
    }

    public ReactiveRedisMessageListenerContainer getListener() {
        return listener;
    }

    public Sinks.Many<T> getSink() {
        return sink;
    }

    public ReactiveRedisTemplate<String, T> getReactiveRedisTemplate() {
        return reactiveRedisTemplate;
    }

    @Override
    public int hashCode() {
        return ws.getId().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final WebSocketRedisListener other = (WebSocketRedisListener) obj;
        return this.ws.getId().endsWith(other.getWs().getId());
    }

    @Override
    public void destroy() {
        if (ws.isOpen()) {
            ws.close(CloseStatus.NORMAL).subscribe(v -> {
                logger.info("關閉 WetSocket:{} 連線", ws.getId());
            });
        } else {
            logger.info("WetSocket:{} 連線早已關閉", ws.getId());
        }
        listener.getActiveSubscriptions().removeIf(s -> {
            s.unsubscribe().doFinally(signal -> {
                logger.info("退訂頻道:{} with session:{}", topic, ws.getId());
            });
            return true;
        });
        listener.destroyLater().subscribe(v -> {
            logger.info("Destroy");
        }, t -> {
            logger.info("Destroy error {}", t.getMessage());
        });
    }
}

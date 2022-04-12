package wf.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.fppt.jedismock.RedisServer;
import java.time.Duration;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurerSupport;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AdviceMode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisSocketConfiguration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;
import org.springframework.data.redis.core.ReactiveHashOperations;
import org.springframework.data.redis.core.ReactiveListOperations;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveSetOperations;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.data.redis.core.ReactiveZSetOperations;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import wf.util.LettuceConnFactoryCondition;

/**
 *
 * @author Kent Yeh
 */
@Configuration
@EnableCaching(mode = AdviceMode.PROXY)
public class RedisCacheConfig extends CachingConfigurerSupport {

    private static final Logger logger = LogManager.getLogger(RedisCacheConfig.class);
    private ApplicationContext context;

    @Autowired
    public void setContext(ApplicationContext context) {
        this.context = context;
    }

    @Bean
    public GenericObjectPoolConfig genericObjectPoolConfig() {
        GenericObjectPoolConfig config = new GenericObjectPoolConfig();
        config.setMaxTotal(Runtime.getRuntime().availableProcessors());
        config.setMaxIdle(1);
        config.setTestOnBorrow(true);
        config.setTestOnReturn(true);
        config.setTestWhileIdle(false);
        return config;
    }

    @Bean("prod")
    @Conditional(LettuceConnFactoryCondition.class)
    public LettuceConnectionFactory redisProdConnectionFactory(GenericObjectPoolConfig gopc) {
        logger.info("採用正式環境：連到正式Redis主機");
        RedisSocketConfiguration config = new RedisSocketConfiguration("/tmp/redis.sock");
        gopc.setMaxTotal(10);
        gopc.setMaxIdle(3);
        gopc.setMinIdle(1);
        gopc.setMaxWait(Duration.ofSeconds(10));
        LettucePoolingClientConfiguration poolConfig = LettucePoolingClientConfiguration.builder()
                .poolConfig(gopc).build();
        return new LettuceConnectionFactory(config, poolConfig);
    }

    @Bean("dev")
    @Conditional(LettuceConnFactoryCondition.class)
    public LettuceConnectionFactory redisDevConnectionFactory(GenericObjectPoolConfig gopc) {
        logger.info("採用測試環境：連到測試Redis Mock");
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration("localhost", 
                context.getBean(RedisServer.class).getBindPort());

        LettucePoolingClientConfiguration poolConfig = LettucePoolingClientConfiguration.builder()
                .poolConfig(gopc).build();
        return new LettuceConnectionFactory(config, poolConfig);
    }

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory lettuceConnectionFactory) {
        return RedisCacheManager.create(lettuceConnectionFactory);
    }

    @Bean
    @Scope(BeanDefinition.SCOPE_PROTOTYPE)
    public ReactiveRedisMessageListenerContainer container(ReactiveRedisConnectionFactory lettuceConnectionFactory) {
        return new ReactiveRedisMessageListenerContainer(lettuceConnectionFactory);
    }

//************************以下暫時用不到************
    @Bean("redisJsonNodeTemplate")
    public ReactiveRedisTemplate<String, JsonNode> redisJsonNodeTemplate(ReactiveRedisConnectionFactory lettuceConnectionFactory) {
        Jackson2JsonRedisSerializer jsonRedisSerializer = new Jackson2JsonRedisSerializer(JsonNode.class);
        RedisSerializationContext<String, JsonNode> serializationContext
                = RedisSerializationContext.<String, JsonNode>newSerializationContext(RedisSerializer.string())
                        .value(jsonRedisSerializer).build();
        //或者用下列方式建立編解碼
        //RedisSerializationContext<String, JsonNode> context = 
        //        RedisSerializationContext.newSerializationContext(RedisSerializer.string())
        //        .value(jsonRedisSerializer).build();
        return new ReactiveRedisTemplate<>(lettuceConnectionFactory, serializationContext);
    }

    /*Non Reactive*/
 /*@Bean
     public RedisMessageListenerContainer xcontainer(RedisConnectionFactory connectionFactory, MessageListenerAdapter listenerAdapter) {
     RedisMessageListenerContainer container = new RedisMessageListenerContainer();
     container.setConnectionFactory(connectionFactory);
     // Multiple can be added messageListener, Configure different switches
     container.addMessageListener(listenerAdapter, new PatternTopic(REDIS_CHANNEL));
     container.addMessageListener(listenerAdapter, new PatternTopic(REDIS_CHANNEL_CLOSE));
     container.addMessageListener(listenerAdapter, new PatternTopic(REDIS_CHANNEL_SEND));
     return container;
     }

     @Bean
     MessageListenerAdapter listenerAdapter(RedisMsg receiver) {
     // 這個地方 是給messageListenerAdapter 傳入一個訊息接受的處理器，利用反射的方法呼叫“receiveMessage”
     // 也有好幾個過載方法，這邊預設呼叫處理器的方法 叫handleMessage 可以自己到原始碼裡面看
     return new MessageListenerAdapter(receiver, "receiveMessage");
     }*/
    @Bean
    @Scope(BeanDefinition.SCOPE_PROTOTYPE)
    public ReactiveHashOperations<String, String, JsonNode> hashOperations(ReactiveRedisTemplate<String, JsonNode> redisTemplate) {
        return redisTemplate.opsForHash();
    }

    @Bean
    @Scope(BeanDefinition.SCOPE_PROTOTYPE)
    public ReactiveValueOperations<String, JsonNode> valueOperations(ReactiveRedisTemplate<String, JsonNode> redisTemplate) {
        return redisTemplate.opsForValue();
    }

    @Bean
    @Scope(BeanDefinition.SCOPE_PROTOTYPE)
    public ReactiveListOperations<String, JsonNode> listOperations(ReactiveRedisTemplate<String, JsonNode> redisTemplate) {
        return redisTemplate.opsForList();
    }

    @Bean
    @Scope(BeanDefinition.SCOPE_PROTOTYPE)
    public ReactiveSetOperations<String, JsonNode> setOperations(ReactiveRedisTemplate<String, JsonNode> redisTemplate) {
        return redisTemplate.opsForSet();
    }

    @Bean
    @Scope(BeanDefinition.SCOPE_PROTOTYPE)
    public ReactiveZSetOperations<String, JsonNode> zSetOperations(ReactiveRedisTemplate<String, JsonNode> redisTemplate) {
        return redisTemplate.opsForZSet();
    }
}

package wf.config;

import com.github.fppt.jedismock.RedisServer;
import io.r2dbc.h2.H2ConnectionFactory;
import io.r2dbc.spi.ConnectionFactory;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Scope;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.r2dbc.convert.R2dbcCustomConversions;
import org.springframework.data.r2dbc.dialect.H2Dialect;
import org.springframework.r2dbc.connection.init.CompositeDatabasePopulator;
import org.springframework.r2dbc.connection.init.ConnectionFactoryInitializer;
import org.springframework.r2dbc.connection.init.ResourceDatabasePopulator;
import reactor.util.Logger;
import wf.model.Member;
import wf.util.Loggers4j2;

/**
 * 測試環境用，啟動Redis Mock Server與H2(並順變建立DB Schema)
 *
 * @author Kent Yeh
 */
@Configuration
@Profile("dev")
public class H2R2dbConfig extends R2dbConfig implements ApplicationListener<ContextClosedEvent> {

    private static final Logger logger = Loggers4j2.getLogger(H2R2dbConfig.class);
    @Value("#{systemProperties['redis.port'] ?: 6379}")
    private int port;

    @Bean(destroyMethod = "close")
    @Override
    public ConnectionFactory connectionFactory() {
        return H2ConnectionFactory.inMemory("r2dbc");
    }

    /**
     * 登記自訂之Converter<br/>
     * 以便能在資料庫與物件中轉換
     * @return
     */
    @Bean
    public R2dbcCustomConversions customConversions() {
        List<Converter<?, ?>> converters = new ArrayList<>();
        converters.add(new Member.MemberReadConverter());
        converters.add(new Member.MemberWriteConverter());
        return R2dbcCustomConversions.of(H2Dialect.INSTANCE, converters);
    }

    /**
     * 啟動後建立資料庫綱要
     * @param connectionFactory
     * @return
     * @throws URISyntaxException 
     */
    @Bean
    public ConnectionFactoryInitializer initializer(ConnectionFactory connectionFactory) throws URISyntaxException {
        ConnectionFactoryInitializer initializer = new ConnectionFactoryInitializer();
        initializer.setConnectionFactory(connectionFactory);
        CompositeDatabasePopulator populator = new CompositeDatabasePopulator();
        populator.addPopulators(new ResourceDatabasePopulator(new ClassPathResource("testSchema.sql")));
        initializer.setDatabasePopulator(populator);
        return initializer;
    }

    /**
     * 順便啟動Redic Mock。
     * destroyMethod 1.0.0 好像不能正常關閉，所以必須偵測關閉事件裡再關一次
     * @return
     * @throws IOException 
     */
    @Bean(destroyMethod = "stop")
    @Scope(BeanDefinition.SCOPE_SINGLETON)
    public RedisServer redisServer() throws IOException {
        RedisServer redisServer = RedisServer.newRedisServer(port);
        redisServer.start();
        return redisServer;
    }

    @Override
    public void onApplicationEvent(ContextClosedEvent e) {
        try {
            redisServer().stop();
        } catch (IOException | NullPointerException ex) {
            logger.error(ex.getMessage(), ex);
        }
    }
}

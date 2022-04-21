package wf.config;

import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import static io.r2dbc.pool.PoolingConnectionFactoryProvider.MAX_SIZE;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import static io.r2dbc.spi.ConnectionFactoryOptions.DATABASE;
import static io.r2dbc.spi.ConnectionFactoryOptions.DRIVER;
import static io.r2dbc.spi.ConnectionFactoryOptions.HOST;
import static io.r2dbc.spi.ConnectionFactoryOptions.PASSWORD;
import static io.r2dbc.spi.ConnectionFactoryOptions.PORT;
import static io.r2dbc.spi.ConnectionFactoryOptions.PROTOCOL;
import static io.r2dbc.spi.ConnectionFactoryOptions.USER;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.r2dbc.convert.R2dbcCustomConversions;
import org.springframework.data.r2dbc.dialect.PostgresDialect;
import reactor.util.Logger;
import wf.model.Member;
import wf.util.Loggers4j2;

/**
 *
 * @author Kent Yeh
 */
@Configuration
@Profile("prod")
public class PostgresR2dbConfig extends R2dbConfig {

    private static final Logger logger = Loggers4j2.getLogger(PostgresR2dbConfig.class);

    @Override
    @Bean("connectionFactory")
    public ConnectionFactory connectionFactory() {
        logger.error("PostgresR2dbConfig start");
        ConnectionFactory connectionFactory = ConnectionFactories.get(ConnectionFactoryOptions.builder()
                .option(DRIVER, "pool")
                .option(PROTOCOL, "postgresql") //"sqlserver"、"mariadb" or "mysql" alternatives
                .option(HOST, "127.0.0.1")
                .option(PORT, 5432)
                .option(USER, "postgres")
                .option(PASSWORD, "qwer1234")
                .option(DATABASE, "WEBFLUX")
                .option(MAX_SIZE, 20)
                .build());
        return new ConnectionPool(ConnectionPoolConfiguration.builder(connectionFactory)
                .maxIdleTime(Duration.ofMinutes(1))
                .build());
    }

    /**
     * register custom converter
     *
     * @return
     */
    @Bean
    public R2dbcCustomConversions customConversions() {
        List<Converter<?, ?>> converters = new ArrayList<>();
        converters.add(new Member.MemberReadConverter());
        converters.add(new Member.MemberWriteConverter());
        return R2dbcCustomConversions.of(PostgresDialect.INSTANCE, converters);
    }

}

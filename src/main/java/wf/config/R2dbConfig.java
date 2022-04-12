package wf.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.r2dbc.config.AbstractR2dbcConfiguration;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;

/**
 *
 * @author Kent Yeh
 */
@Configuration
@EnableR2dbcRepositories(basePackageClasses = {wf.data.MemberDao.class})
public abstract class R2dbConfig extends AbstractR2dbcConfiguration {

}

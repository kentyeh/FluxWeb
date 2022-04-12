package wf.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.security.web.server.csrf.CsrfToken;
import org.springframework.security.web.server.csrf.DefaultCsrfToken;
import org.springframework.security.web.server.csrf.ServerCsrfTokenRepository;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import wf.model.CaptchaUserDetailsReactiveAuthenticationManager;
import wf.security.CustomServerFormLoginAuthenticationConverter;
import wf.spring.RedirectFluxWebAuthenticationFailureHandler;
import wf.util.Loggers4j2;

/**
 *
 * @author kent
 */
@EnableWebFluxSecurity
@DependsOn("serverLogoutSuccessHandler")
@EnableReactiveMethodSecurity
public class TestSecConfig extends SecConfig {

    private static final reactor.util.Logger logger = Loggers4j2.getLogger(TestSecConfig.class);
    @Value("#{systemProperties['captcha'] ?: '1234'}")
    private String csrf;

    @Override
    @Bean
    public SecurityWebFilterChain securitygWebFilterChain(ServerHttpSecurity http) {
        SecurityWebFilterChain build = http
                .authorizeExchange()
                .pathMatchers("/", "/index", "/hello/**", "/static/**", "/login", "/logout", "/ocs").permitAll()
                .pathMatchers("/admin/**").hasAuthority("ROLE_ADMIN")
                .anyExchange()
                .authenticated()
                .and().csrf(c -> c.csrfTokenRepository(new FixedCsrfTokenRepository(csrf)))
                .formLogin((ServerHttpSecurity.FormLoginSpec flt) -> {
                    flt.authenticationManager(new CaptchaUserDetailsReactiveAuthenticationManager(userDetailsService()));
                    flt.loginPage("/login");
                    flt.authenticationFailureHandler(new RedirectFluxWebAuthenticationFailureHandler("/login?error"));
                })
                .logout()
                .logoutUrl("/logout").requiresLogout(ServerWebExchangeMatchers.pathMatchers(HttpMethod.GET, "/logout"))
                .logoutSuccessHandler(getLogoutHandler())
                .and().build();
        build.getWebFilters().subscribe(filter -> {
            if (filter instanceof AuthenticationWebFilter) {
                AuthenticationWebFilter awf = (AuthenticationWebFilter) filter;
                awf.setServerAuthenticationConverter(new CustomServerFormLoginAuthenticationConverter());
            }
        });
        return build;
    }

    private static class FixedCsrfTokenRepository implements ServerCsrfTokenRepository {

        private final String csrf;

        public FixedCsrfTokenRepository(String csrf) {
            this.csrf = csrf;
        }

        @Override
        public Mono<CsrfToken> generateToken(ServerWebExchange exchange) {
            return Mono.fromCallable(() -> new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", csrf));
        }

        @Override
        public Mono<Void> saveToken(ServerWebExchange exchange, CsrfToken token) {
            return Mono.empty();
        }

        @Override
        public Mono<CsrfToken> loadToken(ServerWebExchange exchange) {
            return generateToken(exchange);
        }
    }
}

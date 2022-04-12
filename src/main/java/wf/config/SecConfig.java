package wf.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.security.web.server.authentication.logout.ServerLogoutSuccessHandler;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import wf.data.MemberManager;
import wf.model.MemberDetailService;
import wf.security.CustomServerFormLoginAuthenticationConverter;
import wf.model.CaptchaUserDetailsReactiveAuthenticationManager;
import wf.spring.RedirectFluxWebAuthenticationFailureHandler;
import wf.util.Loggers4j2;

/**
 *
 * @author Kent Yeh
 */
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
@DependsOn("serverLogoutSuccessHandler")
public class SecConfig {

    private static final reactor.util.Logger logger = Loggers4j2.getLogger(SecConfig.class);

    private MemberManager manager;
    //private final RedirectServerLogoutSuccessHandler logoutHandler = new RedirectServerLogoutSuccessHandler();
    private ServerLogoutSuccessHandler logoutHandler;

    @Autowired
    public void setManager(MemberManager manager) {
        this.manager = manager;
    }

    @Autowired
    public void setLogoutHandler(ServerLogoutSuccessHandler logoutHandler) {
        this.logoutHandler = logoutHandler;
    }

    public ServerLogoutSuccessHandler getLogoutHandler() {
        return logoutHandler;
    }

    /*@Autowired
     private AuthenticationManager authenticationManager;

     @Autowired
     private SecurityContextRepository securityContextRepository;

     @Bean
     public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
     Disable default security.
     http.httpBasic().disable();
     http.formLogin().disable();
     http.csrf().disable();
     http.logout().disable();
     http.authenticationManager(this.authenticationManager);
     http.securityContextRepository(this.securityContextRepository);
     logger.error("http is {}",http.getClass());
     http.authorizeExchange().pathMatchers("/admin/**").hasAuthority("ROLE_ADMIN");
     http.authorizeExchange().anyExchange().authenticated();
        
     return http.build();

     }*/
    @Bean
    public SecurityWebFilterChain securitygWebFilterChain(ServerHttpSecurity http) {
//        logoutHandler.setLogoutSuccessUrl(URI.create("/"));
        SecurityWebFilterChain build = http
                .authorizeExchange()
                .pathMatchers("/", "/index", "/hello/**", "/static/**", "/login", "/logout", "/ocs").permitAll()
                .pathMatchers("/admin/**").hasAuthority("ROLE_ADMIN")
                .anyExchange()
                .authenticated()
                .and().csrf().and()
                .formLogin((ServerHttpSecurity.FormLoginSpec flt) -> {
                    flt.authenticationManager(new CaptchaUserDetailsReactiveAuthenticationManager(userDetailsService()));
                    flt.loginPage("/login");
                    flt.authenticationFailureHandler(new RedirectFluxWebAuthenticationFailureHandler("/login?error"));
                })
                .logout()
                .logoutUrl("/logout").requiresLogout(ServerWebExchangeMatchers.pathMatchers(HttpMethod.GET, "/logout"))
                .logoutSuccessHandler(logoutHandler)
                .and().build();
        build.getWebFilters().subscribe(filter -> {
            if (filter instanceof AuthenticationWebFilter) {
                AuthenticationWebFilter awf = (AuthenticationWebFilter) filter;
                awf.setServerAuthenticationConverter(new CustomServerFormLoginAuthenticationConverter());
            }
        });
        return build;
    }

    @Bean
    public ReactiveUserDetailsService userDetailsService() {
        return new MemberDetailService(manager);
    }
}

package wf.config;

import io.netty.channel.ChannelOption;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import org.apache.commons.lang3.LocaleUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Scope;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.i18n.LocaleContext;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.format.FormatterRegistry;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.http.server.reactive.ReactorHttpHandlerAdapter;
import org.springframework.session.data.redis.config.annotation.web.server.EnableRedisWebSession;
import org.springframework.web.reactive.accept.RequestedContentTypeResolverBuilder;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.reactive.config.ResourceHandlerRegistry;
import org.springframework.web.reactive.config.ViewResolverRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import org.springframework.web.reactive.result.method.HandlerMethodArgumentResolver;
import org.springframework.web.reactive.result.method.annotation.ArgumentResolverConfigurer;
import org.springframework.web.reactive.result.view.ViewResolver;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.adapter.WebHttpHandlerBuilder;
import org.springframework.web.server.i18n.LocaleContextResolver;
import reactor.netty.DisposableServer;
import reactor.netty.http.HttpProtocol;
import wf.data.MemberManager;
import wf.model.MemberFormatter;

/**
 *
 * @author Kent Yeh
 */
@Configuration
@Import(RedisCacheConfig.class)
@EnableWebFlux
@EnableRedisWebSession(maxInactiveIntervalInSeconds = 30 * 60)
public class WebConfig implements WebFluxConfigurer, DisposableBean, ApplicationListener<ContextClosedEvent> {

    private static final Logger logger = LogManager.getLogger(WebConfig.class);
    private org.springframework.context.ApplicationContext context;
    @Value("#{systemProperties['http.port'] ?: 80}")
    private int port;

    private ViewResolver viewResolver;

    private MemberManager memberManager;

    private HandlerMethodArgumentResolver authenticationPrincipalArgumentResolver=null;

    @Autowired(required = false)
    @Qualifier("testAuthenticationPrincipalResolver")
    public void setAuthenticationPrincipalArgumentResolver(HandlerMethodArgumentResolver authenticationPrincipalArgumentResolver) {
        this.authenticationPrincipalArgumentResolver = authenticationPrincipalArgumentResolver;
    }

    

    @Autowired
    @Qualifier("thymeleafReactiveViewResolver")
    public void setViewResolver(ViewResolver viewResolver) {
        this.viewResolver = viewResolver;
    }

    @Autowired
    public void setManager(MemberManager memberManager) {
        this.memberManager = memberManager;
    }

    @Override
    public void configureContentTypeResolver(RequestedContentTypeResolverBuilder builder) {
        builder.parameterResolver().mediaType("json", MediaType.APPLICATION_JSON);
        builder.parameterResolver().mediaType("html", MediaType.TEXT_HTML);
    }

    @Autowired
    public void setContext(org.springframework.context.ApplicationContext context) {
        this.context = context;
    }

    @Override
    public void onApplicationEvent(ContextClosedEvent e) {
        try {
            destroy();
        } catch (IOException ex) {
            logger.fatal(ex.getMessage(), ex);
        }
    }

    @Override
    public void destroy() throws IOException {
        httpServer().disposeNow(Duration.ofMinutes(1));
    }

    /**
     * Build Web Server
     *
     * @return
     */
    @Bean
    @Scope(BeanDefinition.SCOPE_SINGLETON)
    public DisposableServer httpServer() {
        HttpHandler httpHandler = WebHttpHandlerBuilder.applicationContext(context)
                .localeContextResolver(localeContextResolver()).build();
        ReactorHttpHandlerAdapter adapter = new ReactorHttpHandlerAdapter(httpHandler);
        return reactor.netty.http.server.HttpServer.create().host("0.0.0.0").port(port)
                .protocol(HttpProtocol.HTTP11, HttpProtocol.H2C)
                .noSSL().compress(true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                .wiretap(false)
                .accessLog(false)
                .handle(adapter).bindNow();
    }

    @Override
    public void configureHttpMessageCodecs(ServerCodecConfigurer configurer) {
        configurer.defaultCodecs().enableLoggingRequestDetails(true);
    }

    @Override
    public void configureViewResolvers(ViewResolverRegistry registry) {
        registry.viewResolver(viewResolver);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/static/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/")
                .addResourceLocations("classpath:/static/")
                .resourceChain(false);
    }

    @Override
    public void configureArgumentResolvers(ArgumentResolverConfigurer configurer) {
        if (this.authenticationPrincipalArgumentResolver != null) {
            configurer.addCustomResolver(this.authenticationPrincipalArgumentResolver);
        }
        WebFluxConfigurer.super.configureArgumentResolvers(configurer);
    }

    /*@Override
    public void configureArgumentResolvers(ArgumentResolverConfigurer configurer) {
        configurer.addCustomResolver(new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                boolean res = parameter.getParameterAnnotation(AuthenticationPrincipal.class) != null;
                return res;
            }

            @Override
            public Mono<Object> resolveArgument(MethodParameter parameter, BindingContext bindingContext, ServerWebExchange exchange) {
                bindingContext.getModel().asMap().forEach((k, v) -> {
                    logger.error("ctx[{} ]= {}", k, v);
                });
                return exchange.getSession().map(ws -> {
                    Object obj = ws.getAttributes().get(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
                    ws.getAttributes().forEach((k, v) -> {
                    logger.error("session[{} ]= {}", k, v);
                });
                    return parameter.getParameterType().isAssignableFrom(Mono.class) ? Mono.just(obj) : obj;
                });
            }
        });
        WebFluxConfigurer.super.configureArgumentResolvers(configurer);
    }*/
    /**
     * Change Locale by reuqest parameter &quot;locale&quot;
     *
     * @return
     */
    @Bean
    public LocaleContextResolver localeContextResolver() {
        return new LocaleContextResolver() {

            @Override
            public LocaleContext resolveLocaleContext(ServerWebExchange exchange) {
                Locale targetLocale = Locale.getDefault();
                List<String> referLang = exchange.getRequest().getQueryParams().get("locale");
                if (referLang != null && !referLang.isEmpty()) {
                    String lang = referLang.get(0);
                    targetLocale = LocaleUtils.toLocale(lang);
                    LocaleContextHolder.setLocale(targetLocale, true);
                }
                if (LocaleContextHolder.getLocaleContext() == null) {
                    LocaleContextHolder.setLocale(targetLocale, true);
                }
                return LocaleContextHolder.getLocaleContext();
            }

            @Override
            public void setLocaleContext(ServerWebExchange swe, LocaleContext lc) {
                LocaleContextHolder.setLocaleContext(lc);
            }
        };
    }

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addFormatter(new MemberFormatter(memberManager));
    }

}

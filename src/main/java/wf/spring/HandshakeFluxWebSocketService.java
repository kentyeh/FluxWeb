package wf.spring;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.server.RequestUpgradeStrategy;
import org.springframework.web.reactive.socket.server.support.HandshakeWebSocketService;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.util.Logger;
import wf.util.Loggers4j2;

/**
 *
 * @author Kent Yeh
 */
public class HandshakeFluxWebSocketService extends HandshakeWebSocketService implements InitializingBean {

    private static final Logger logger = Loggers4j2.getLogger(HandshakeFluxWebSocketService.class);

    public HandshakeFluxWebSocketService() {
        super();
    }

    public HandshakeFluxWebSocketService(RequestUpgradeStrategy upgradeStrategy) {
        super(upgradeStrategy);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        setSessionAttributePredicate(s -> {
            logger.info("轉遞 ({}) 給 WebSoketHandler", s);
            return true;
        });
    }

    @Override
    public Mono<Void> handleRequest(ServerWebExchange exchange, WebSocketHandler handler) {
        exchange.getSession().subscribe(ws -> {
            SecurityContext sc = ws.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
            if (sc != null) {
                logger.info("HandshakeFluxWebSocketService-principal is [{}]{}", sc.getAuthentication().getPrincipal().getClass().getName(), sc.getAuthentication().getPrincipal());
            }
            ws.getAttributes().put("JSESSIONID", ws.getId());
        });
        return super.handleRequest(exchange, handler);
    }

    public Mono<Void> handleRequest4DebugMessage(ServerWebExchange exchange, WebSocketHandler handler) {
        ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(Authentication::getName)
                .subscribe(s -> logger.info("ReactiveSecurityContextHolder with {}", s));
        exchange.getSession().subscribe(ws -> {
            ws.getAttributes().entrySet().forEach(entry -> {
                logger.debug("HandshakeFluxWebSocketService-sessions[{}]={}", entry.getKey(), entry.getValue());
            });
        });
        exchange.getPrincipal().subscribe(p -> {
            logger.debug("HandshakeFluxWebSocketService-exchange.getPrincipal() is {}", p);
        });
        exchange.getFormData().subscribe(fd -> {
            fd.forEach((t, u) -> logger.debug("HandshakeFluxWebSocketService-form[{}]={}", t, u.get(0)));
        });
        exchange.getSession().subscribe(ws -> {
            SecurityContext sc = ws.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
            if (sc != null) {
                logger.info("HandshakeFluxWebSocketService-principal is [{}]{}", sc.getAuthentication().getPrincipal().getClass().getName(), sc.getAuthentication().getPrincipal());
            }
            ws.getAttributes().put("JSESSIONID", ws.getId());
        });
        exchange.getAttributes().forEach((k, v) -> {
            logger.info("HandshakeFluxWebSocketService-Attribute[{}]={}", k, v);
        });
        return super.handleRequest(exchange, handler);
    }
}

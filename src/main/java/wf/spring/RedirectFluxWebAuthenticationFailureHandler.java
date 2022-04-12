package wf.spring;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.security.web.server.authentication.RedirectServerAuthenticationFailureHandler;
import reactor.core.publisher.Mono;

/**
 * 把登錄存失敗的 Username 儲存到Session裡
 *
 * @author Kent Yeh
 */
public class RedirectFluxWebAuthenticationFailureHandler extends RedirectServerAuthenticationFailureHandler {

    private String sessionKey = "LAST_USERNAME";
    private String paramKey = "username";

    public RedirectFluxWebAuthenticationFailureHandler(String location) {
        super(location);
    }

    public RedirectFluxWebAuthenticationFailureHandler(String location, String sessionKey, String paramKey) {
        super(location);
        this.sessionKey = sessionKey;
        this.paramKey = paramKey;
    }

    @Override
    public Mono<Void> onAuthenticationFailure(WebFilterExchange exchange, AuthenticationException exception) {
        exchange.getExchange().getSession().subscribe(ws -> {
            exchange.getExchange().getFormData().filter(mm -> mm.containsKey(paramKey)).subscribe(mm -> {
                ws.getAttributes().put(sessionKey, mm.getFirst(paramKey));
            });
        });
        return super.onAuthenticationFailure(exchange, exception);
    }
}

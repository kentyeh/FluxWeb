package wf.security;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.util.Assert;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.util.Logger;
import wf.util.Loggers4j2;

/**
 *
 * @author Kent Yeh
 */
public class CustomServerFormLoginAuthenticationConverter implements ServerAuthenticationConverter {

    private static final Logger logger = Loggers4j2.getLogger(CustomServerFormLoginAuthenticationConverter.class);

    private String usernameParameter = "username";

    private String passwordParameter = "password";

    private String captchaParameter = "captcha";

    private String ignreCasePatameter = "ignoreCase";

    @Override
    public Mono<Authentication> convert(ServerWebExchange exchange) {
        return exchange.getFormData().map((data) -> createAuthentication(exchange, data));
    }

    private UsernamePasswordAuthenticationToken createAuthentication(ServerWebExchange exchange, MultiValueMap<String, String> data) {
        String username = data.getFirst(this.usernameParameter);
        String password = data.getFirst(this.passwordParameter);
        String captcha = data.getFirst(this.captchaParameter);
        String ic = data.getFirst(this.ignreCasePatameter);
        boolean ignoreCase = !"N".equalsIgnoreCase(ic) && !"FALSE".equalsIgnoreCase(ic);
        return new UsernamePasswordCaptchaAuthenticationToken(username, password, captcha, exchange.getSession(), this.captchaParameter, ignoreCase);
    }

    public void setUsernameParameter(String usernameParameter) {
        Assert.notNull(usernameParameter, "usernameParameter cannot be null");
        this.usernameParameter = usernameParameter;
    }

    public void setPasswordParameter(String passwordParameter) {
        Assert.notNull(passwordParameter, "passwordParameter cannot be null");
        this.passwordParameter = passwordParameter;
    }

    public void setCaptchaParameter(String captchaParameter) {
        Assert.notNull(captchaParameter, "captchaParameter cannot be null");
        this.captchaParameter = captchaParameter;
    }

    public void setIgnreCase(String ignoreCaseParameter) {
        Assert.notNull(ignoreCaseParameter, "ignoreCaseParameter cannot be null");
        this.ignreCasePatameter = ignoreCaseParameter;
    }
}

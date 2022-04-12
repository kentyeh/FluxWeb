package wf.security;

import java.util.Collection;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.SpringSecurityCoreVersion;
import org.springframework.web.server.WebSession;
import reactor.core.publisher.Mono;

/**
 *
 * @author kent
 */
public class UsernamePasswordCaptchaAuthenticationToken extends UsernamePasswordAuthenticationToken {

    private static final long serialVersionUID = SpringSecurityCoreVersion.SERIAL_VERSION_UID;

    private final String captcha;

    private final boolean ignoreCase;

    private final Mono<WebSession> session;

    private final String parameter;

    public UsernamePasswordCaptchaAuthenticationToken(Object principal, Object credentials, String captcha, Mono<WebSession> session, String parameter, boolean ignoreCase) {
        super(principal, credentials);
        this.captcha = captcha;
        this.session = session;
        this.parameter = parameter;
        this.ignoreCase = ignoreCase;
    }

    public UsernamePasswordCaptchaAuthenticationToken(Object principal, Object credentials,
            String captcha, Mono<WebSession> session, String parameter, boolean ignoreCase, Collection<? extends GrantedAuthority> authorities) {
        super(principal, credentials, authorities);
        this.captcha = captcha;
        this.session = session;
        this.ignoreCase = ignoreCase;
        this.parameter = parameter;
        super.setAuthenticated(true); // must use super, as we override
    }

    /**
     * @return 用戶輸入的驗證碼
     */
    public String getCaptcha() {
        return captcha;
    }

    /**
     * @return 驗證碼是否不分大小寫
     */
    public boolean isIgnoreCase() {
        return ignoreCase;
    }

    public Mono<WebSession> getSession() {
        return session;
    }

    /**
     * @return 驗證碼儲存在Session的Key
     */
    public String getParameter() {
        return parameter;
    }

}

package wf.model;

import org.springframework.security.authentication.AbstractUserDetailsReactiveAuthenticationManager;
import org.springframework.security.authentication.AccountExpiredException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.ReactiveUserDetailsPasswordService;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsChecker;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.Logger;
import wf.util.Loggers4j2;
import wf.security.UsernamePasswordCaptchaAuthenticationToken;

/**
 *
 * @author Kent Yeh
 */
@Component
public class CaptchaUserDetailsReactiveAuthenticationManager extends AbstractUserDetailsReactiveAuthenticationManager {

    private static final Logger logger = Loggers4j2.getLogger(CaptchaUserDetailsReactiveAuthenticationManager.class);
    private ReactiveUserDetailsPasswordService userDetailsPasswordService;
    private final ReactiveUserDetailsService userDetailsService;

    public CaptchaUserDetailsReactiveAuthenticationManager(ReactiveUserDetailsService userDetailsService) {
        this.userDetailsService = userDetailsService;
    }

    private final PasswordEncoder passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    private final UserDetailsChecker preAuthenticationChecks = this::defaultPreAuthenticationChecks;

    private UserDetailsChecker postAuthenticationChecks = this::defaultPostAuthenticationChecks;

    private void defaultPreAuthenticationChecks(UserDetails user) {
        if (!user.isAccountNonLocked()) {
            logger.debug("用戶帳號已被鎖定");
            throw new LockedException(this.messages.getMessage("AbstractUserDetailsAuthenticationProvider.locked",
                    "用戶帳號已被鎖定"));
        }
        if (!user.isEnabled()) {
            logger.debug("用戶帳號未啟用");
            throw new DisabledException(
                    this.messages.getMessage("AbstractUserDetailsAuthenticationProvider.disabled", "用戶帳號未啟用"));
        }
        if (!user.isAccountNonExpired()) {
            logger.debug("用戶帳號逾期");
            throw new AccountExpiredException(this.messages
                    .getMessage("AbstractUserDetailsAuthenticationProvider.expired", "用戶帳號逾期"));
        }
    }

    private void defaultPostAuthenticationChecks(UserDetails user) {
        if (!user.isCredentialsNonExpired()) {
            logger.debug("用戶帳號/密碼逾期");
            throw new CredentialsExpiredException(this.messages.getMessage(
                    "AbstractUserDetailsAuthenticationProvider.credentialsExpired", "User credentials have expired"));
        }
    }

    @Override
    protected Mono<UserDetails> retrieveUser(String username) {
        return this.userDetailsService.findByUsername(username);
    }

    @Override
    public Mono<Authentication> authenticate(Authentication authentication) {
        UsernamePasswordCaptchaAuthenticationToken token = (UsernamePasswordCaptchaAuthenticationToken) authentication;
        String username = token.getName();
        String inputPassword = (String) token.getCredentials();
        return token.getSession().filter(session -> session.getAttribute(token.getParameter()) != null)
                .<String>map(session -> session.getAttribute(token.getParameter()))
                .log(logger)
                .filter(saved -> token.isIgnoreCase() ? saved.equalsIgnoreCase(token.getCaptcha()) : saved.equals(token.getCaptcha()))
                .switchIfEmpty(Mono.defer(() -> {
                    logger.error("無效的驗證碼");
                    return Mono.error(new BadCredentialsException("無效的驗證碼"));
                }))
                .then(token.getSession().map(session -> session.getAttributes().remove(token.getParameter())))//移除Session的驗證碼以免被萬年驗證
                .then(retrieveUser(username))
                .doOnNext(this.preAuthenticationChecks::check)
                .publishOn(Schedulers.boundedElastic())
                .filter((userDetails) -> this.passwordEncoder.matches(inputPassword, userDetails.getPassword()))
                .switchIfEmpty(Mono.defer(() -> Mono.error(new BadCredentialsException("無效的帳號/密碼"))))
                .flatMap((userDetails) -> upgradeEncodingIfNecessary(userDetails, inputPassword))
                .doOnNext(this.postAuthenticationChecks::check)
                .map(this::createUsernamePasswordAuthenticationToken);
    }

    private Mono<UserDetails> upgradeEncodingIfNecessary(UserDetails userDetails, String presentedPassword) {
        boolean upgradeEncoding = this.userDetailsPasswordService != null
                && this.passwordEncoder.upgradeEncoding(userDetails.getPassword());
        if (upgradeEncoding) {
            String newPassword = this.passwordEncoder.encode(presentedPassword);
            return this.userDetailsPasswordService.updatePassword(userDetails, newPassword);
        }
        return Mono.just(userDetails);
    }

    @Override
    public void setUserDetailsPasswordService(ReactiveUserDetailsPasswordService userDetailsPasswordService) {
        this.userDetailsPasswordService = userDetailsPasswordService;
    }

    @Override
    public void setPostAuthenticationChecks(UserDetailsChecker postAuthenticationChecks) {
        Assert.notNull(this.postAuthenticationChecks, "postAuthenticationChecks cannot be null");
        this.postAuthenticationChecks = postAuthenticationChecks;
    }

    private UsernamePasswordAuthenticationToken createUsernamePasswordAuthenticationToken(UserDetails userDetails) {
        return new UsernamePasswordAuthenticationToken(userDetails, userDetails.getPassword(),
                userDetails.getAuthorities());
    }
}

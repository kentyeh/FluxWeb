package wf.spring;

import org.assertj.core.api.Assertions;
import io.netty.handler.codec.http.cookie.Cookie;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveSecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.security.test.context.support.ReactorContextTestExecutionListener;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.context.support.WithSecurityContextTestExecutionListener;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testng.annotations.Test;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.DisposableServer;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.HttpClientRequest;
import reactor.util.Logger;
import wf.config.TestContext;
import wf.config.TestSecConfig;
import wf.model.MemberDetails;
import wf.util.Loggers4j2;

/**
 *
 * @author kent
 */
@WebFluxTest(controllers = RootController.class, excludeAutoConfiguration = {ReactiveSecurityAutoConfiguration.class})
@TestExecutionListeners({ReactorContextTestExecutionListener.class,WithSecurityContextTestExecutionListener.class})
@ContextConfiguration(classes = {TestContext.class, TestSecConfig.class})
public class TestWS extends AbstractTestNGSpringContextTests {
    
    private static final Logger logger = Loggers4j2.getLogger(TestWS.class);
    @Value("#{systemProperties['captcha'] ?: '1234'}")
    private String defaultCaptcha;
    
    private DisposableServer httpServer;
    
    private WebTestClient webClient;
    
    @Autowired
    public void setWebClient(WebTestClient webClient) {
        this.webClient = webClient;
    }
    
    @Autowired
    public void setHttpServer(DisposableServer httpServer) {
        this.httpServer = httpServer;
    }

    /**
     * withMockUser look
     * https://github.com/spring-projects/spring-security/blob/main/test/src/main/java/org/springframework/security/test/context/support/WithMockUserSecurityContextFactory.java
     * here not work
     */
    @Test(expectedExceptions = AssertionError.class)
    @WithMockUser(username = "root", password = "webflux", authorities = "ROLE_ADMIN")
    void testAdminError() {
        webClient.get().uri("/admin").exchange().expectStatus().isOk()
                .expectBody().consumeWith(respon
                        -> Assertions.assertThat(new String(respon.getResponseBody(), StandardCharsets.UTF_8)).isEqualTo("Hello Administrator!")
                );
    }
    
    @Test
    void testAdmin() {
        webClient.mutateWith(mockUser("root").roles("ADMIN"))
                .get().uri("/admin").exchange().expectStatus().isOk()
                .expectBody().consumeWith(respon
                        -> Assertions.assertThat(new String(respon.getResponseBody(), StandardCharsets.UTF_8)).isEqualTo("Hello Administrator!")
                );
    }

    void testWs() throws IOException, InterruptedException {
        reactor.netty.http.client.HttpClient client = reactor.netty.http.client.HttpClient.create();
        AtomicReference<Cookie> session = new AtomicReference();
        CountDownLatch waitLogin = new CountDownLatch(1);
        client.get().uri("http://localhost:" + httpServer.port() + "/static/captcha")
                .response((res, buf) -> {
                    res.cookies().entrySet().forEach(entry -> {
                        entry.getValue().forEach(c -> {
                            logger.info("before cookie:{}", c);
                        });
                    });
                    session.set(res.cookies().get("SESSION").iterator().next());
                    return Mono.just(res.status().code());
                }).filter(code -> code == 200)
                .flatMap(code -> client.cookie(session.get())
                        .post().uri("http://localhost:" + httpServer.port() + "/login")
                        .sendForm((req, form) -> form.attr("_csrf", defaultCaptcha).attr("username", "nobody")
                                .attr("password", "nobody").attr("captcha", defaultCaptcha))
                        .responseSingle((res, buf) -> {
                            res.cookies().entrySet().forEach(entry -> {
                                entry.getValue().forEach(c -> {
                                    logger.info("after cookie:{}", c);
                                });
                            });
                            session.set(res.cookies().get("SESSION").iterator().next());
                            return Mono.just(res.status().code());
                        })
                ).filter(code -> code == 302)
                .doFinally(st -> waitLogin.countDown())
                .subscribe(code -> Assertions.assertThat(code).as("登錄失敗").isEqualTo(302));
        waitLogin.await();
        CountDownLatch waitMsg = new CountDownLatch(1);
        client.cookie(session.get())
                .websocket().uri("ws://localhost:" + httpServer.port() + "/chat")
                .handle((inb, oub) -> {
                    return inb.receive()
                    .map(buf -> buf.toString(StandardCharsets.UTF_8))
                    .handle((text, sink) -> {
                        Assertions.assertThat(text).as("沒有收到物回被放入購物車的訊息")
                        .contains("您剛把東西放進購物車");
                        sink.next(text);
                        sink.complete();
                    })
                    .then();
                })
                .doOnSubscribe(sb -> {
                    sb.request(1);
                }).subscribe();
        //We cannot detect when websocket is handshake over.
        Thread.sleep(3000);
        reactor.netty.http.client.HttpClient.create().cookie(session.get())
                .post().uri("http://localhost:" + httpServer.port() + "/putmyshtoppingcart")
                .sendForm((req, form) -> form.attr("_csrf", defaultCaptcha))
                .responseSingle((res, buf) -> Mono.just(res.status().code()))
                .subscribe(code -> logger.info("sec code is {}", code));
        waitMsg.await(15, TimeUnit.SECONDS);
    }
    
}

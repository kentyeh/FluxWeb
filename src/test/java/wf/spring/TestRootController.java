package wf.spring;

import java.nio.charset.StandardCharsets;
import org.assertj.core.api.Assertions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveSecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.ReactorContextTestExecutionListener;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.context.support.WithSecurityContextTestExecutionListener;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import reactor.util.Logger;
import wf.config.TestContext;
import wf.model.Member;
import wf.model.MemberDetails;
import wf.util.Loggers4j2;

/**
 * 如果Test的Controller沒有使用 參考
 * https://stackoverflow.com/questions/54504602/failing-test-with-spring-webflux-and-controller
 * <a href="https://docs.spring.io/spring-framework/docs/current/reference/html/web-reactive.html#webflux-ann-arguments">Method
 * Arguments</a>
 * 可以不要用 Heavy 的 @AutoConfigureWebTestClient
 *
 * @author Kent Yeh
 */
@WebFluxTest(controllers = RootController.class, excludeAutoConfiguration = {ReactiveSecurityAutoConfiguration.class})
@TestExecutionListeners({ReactorContextTestExecutionListener.class,WithSecurityContextTestExecutionListener.class})
@ContextConfiguration(classes = {TestContext.class})
//@AutoConfigureWebTestClient
public class TestRootController extends AbstractTestNGSpringContextTests {

    private static final Logger logger = Loggers4j2.getLogger(TestRootController.class);
    @Autowired
    private WebTestClient webClient;

    @BeforeClass
    public void init() {
        logger.info("Web測試啟動中…");
//        webClient = WebTestClient.bindToApplicationContext(applicationContext)
//                .apply(springSecurity()).configureClient()
//                .build();
    }

    @Test
    void testRoot() {
        webClient.get().uri("/").exchange().expectStatus().isOk();
    }

    @Test
    void testAdmin() {
        webClient.get().uri("/admin").exchange().expectStatus().isOk()
                .expectBody().consumeWith(respon
                        -> Assertions.assertThat(new String(respon.getResponseBody(), StandardCharsets.UTF_8)).isEqualTo("Hello Administrator!")
                );

    }

    /**
     * WithMockUser not work here
     */
    @Test
    @WithMockUser(username = "nobody", password = "nobody", authorities = "ROLE_USER")
    void testWhoAmi() {
        Member member = new Member("nobody", "嘸人識君");
        member.setPasswd("nobody");
        member.addRole("ROLE_USER");
        webClient
                .mutateWith(mockUser(new MemberDetails(member)))
//                .mutateWith(mockUser("嘸人識君").roles("USER"))
                .get().uri("/whoami").header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_HTML_VALUE)
                .exchange().expectBody().consumeWith(response
                        -> Assertions.assertThat(new String(response.getResponseBody(), StandardCharsets.UTF_8)).contains("嘸人識君"));
    }

    @Test
    void testHelloNobody() {
        webClient.get().uri("/hello/{member}", "nobody")
                .exchange().expectBody().consumeWith(response
                        -> Assertions.assertThat(new String(response.getResponseBody(), StandardCharsets.UTF_8)).contains("無人識君"));

    }
}

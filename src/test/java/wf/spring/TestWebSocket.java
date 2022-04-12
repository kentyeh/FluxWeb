package wf.spring;

import wf.config.TestSecConfig;
import wf.config.TestContext;
import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.NicelyResynchronizingAjaxController;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.javascript.host.WebSocket;
import io.netty.handler.codec.http.cookie.Cookie;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveSecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.security.test.context.support.ReactorContextTestExecutionListener;
import org.springframework.security.test.context.support.WithSecurityContextTestExecutionListener;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.client.HttpClient;

import reactor.util.Logger;
import wf.util.Loggers4j2;

/**
 *
 * @author kent
 */
@WebFluxTest(excludeAutoConfiguration = {ReactiveSecurityAutoConfiguration.class})
@TestExecutionListeners({ReactorContextTestExecutionListener.class,WithSecurityContextTestExecutionListener.class})
@ContextConfiguration(classes = {TestContext.class, TestSecConfig.class})
public class TestWebSocket extends AbstractTestNGSpringContextTests {

    private static final Logger logger = Loggers4j2.getLogger(TestWebSocket.class);
    @Value("#{systemProperties['captcha'] ?: '1234'}")
    private String defaultCaptcha;

    private WebClient webClient;

    private DisposableServer httpServer;

    @Autowired
    public void setHttpServer(DisposableServer httpServer) {
        this.httpServer = httpServer;
    }

    @BeforeClass
    public void init() {
        logger.info("WebSocket測試啟動中…");
        webClient = new WebClient(BrowserVersion.BEST_SUPPORTED);
        webClient.getOptions().setThrowExceptionOnScriptError(false);//當JS執行出錯的時候是否拋出異常, 這裡選擇不需要
        webClient.getOptions().setThrowExceptionOnFailingStatusCode(false);//當HTTP的狀態非200時是否拋出異常, 這裡選擇不需要
        webClient.getOptions().setActiveXNative(false);
        webClient.getOptions().setDownloadImages(true);
        webClient.getOptions().setCssEnabled(true);
        webClient.getOptions().setJavaScriptEnabled(true);
        webClient.getOptions().setWebSocketEnabled(true);
        webClient.setAjaxController(new NicelyResynchronizingAjaxController());
    }

    @AfterClass
    public void destory() {
        if (webClient != null) {
            webClient.close();
        }
    }

    @Test
    void testWs() throws IOException, InterruptedException {
        HtmlPage loginPage = webClient.getPage("http://localhost:" + httpServer.port() + "/ws");
        HtmlForm form = loginPage.getFirstByXPath("//form");
        form.getInputByName("username").setValueAttribute("nobody");
        form.getInputByName("password").setValueAttribute("nobody");
        form.getInputByName("_csrf").setValueAttribute(defaultCaptcha);
        form.getInputByName("captcha").setValueAttribute(defaultCaptcha);
        HtmlPage chatPage = form.getOneHtmlElementByAttribute("button", "type", "submit").click();
        final CountDownLatch waitws = new CountDownLatch(1);
        webClient.getInternals().addListener((WebSocket ws) -> {
            logger.info("建構 WebSocket");
            waitws.countDown();
            /*Function oriOnOpen = ws.getOnopen();
            ws.setOnopen(new FunctionWrapper(oriOnOpen) {
                @Override
                public Object call(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
                    Object res = oriOnOpen == null ? null : super.call(cx, scope, thisObj, args);
                    waitws.countDown();
                    return res;
                }
            });*/
        });
        logger.info("按連線按紐");
        HtmlPage p = chatPage.getHtmlElementById("online").click();
        waitws.await();
        Thread.sleep(3000);
        logger.info("按購物按紐");
        p = chatPage.getHtmlElementById("buyStaff").click();
        webClient.waitForBackgroundJavaScript(3 * 1000);
        Thread.sleep(3000);
        logger.info(p.getElementsByTagName("textarea").get(0).asXml());
    }

    void xxxxtestWs() {
        HttpClient httpClient = HttpClient.create().host("localhost").port(httpServer.port())
                .headers(headers -> {
                    headers.add("User-Agent", "Mozilla/5.0 (X11; Linux x86_64; rv:98.0) Gecko/20100101 Firefox/98.0")
                    .add("Accept", " text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8");
                })
                .compress(true);

        AtomicReference<Cookie> cs = new AtomicReference<>();
        int code = httpClient.get().uri("/static/captcha")
                .responseSingle((r, buf) -> {
                    r.requestHeaders().entries().forEach(entry -> {
                        logger.info("captcha.requestH[{}]={}", entry.getKey(), entry.getValue());
                    });
                    r.responseHeaders().entries().forEach(entry -> {
                        logger.info("captcha.resonH[{}]={}", entry.getKey(), entry.getValue());
                    });
                    r.cookies().entrySet().forEach(entry -> {
                        entry.getValue().forEach(c -> {
                            cs.set(c);
                            logger.error("captcha-cookie[{}]{}", c.name(), c.value());
                        });
                    });
                    return Mono.just(r.status().code());
                })
                .filter(status -> status == 200)
                .switchIfEmpty(Mono.just(-1))
                .block(Duration.ofSeconds(10));
        logger.info("code is {}", code);
        code = httpClient.headers(headers -> {
            headers.add(cs.get().name(), cs.get().value());
        }).post().uri("/login")
                .sendForm((req, form) -> form.multipart(false)
                        .attr("username", "nobody")
                        .attr("password", "nobody")
                        .attr("_csrf", "1234")
                        .attr("captcha", "1234"))
                .responseSingle((r, buf) -> {
                    buf.asString().subscribe(logger::info);
                    return Mono.just(r.status().code());
                })
                .filter(status -> status == 200)
                .switchIfEmpty(Mono.just(-1))
                .block(Duration.ofSeconds(10));
        logger.info("code is {}", code);
    }
}

package wf;

import java.io.IOException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.AbstractApplicationContext;
import reactor.netty.DisposableServer;
import reactor.util.Logger;
import wf.util.Loggers4j2;

/**
 *
 * @author Kent Yeh
 */
public class Main {

    private static final Logger logger = Loggers4j2.getLogger(Main.class);

    public static void main(String[] args) throws IOException {
        try (AbstractApplicationContext context = new AnnotationConfigApplicationContext(wf.config.AppConfig.class)) {
            context.registerShutdownHook();
            context.getBean(DisposableServer.class).onDispose().block();
        } catch (Exception ex) {
            logger.error(ex.getMessage(), ex);
        }
    }
}

package dk.pop.status.temp.config;

import dk.pop.status.temp.utils.Log;
import org.slf4j.Logger;
import org.springframework.boot.web.server.AbstractConfigurableWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class FreePortCustomizer implements WebServerFactoryCustomizer<AbstractConfigurableWebServerFactory> {
    private static final Logger LOG = Log.getLogger(FreePortCustomizer.class);
    private static final int MAX_TRIES = 20;

    @Override
    public void customize(AbstractConfigurableWebServerFactory factory) {
        int wanted = factory.getPort();
        if (wanted <= 0) {
            return;
        }

        for (int port = wanted; port < wanted + MAX_TRIES && port <= 65535; port++) {
            if (isFree(port)) {
                if (port != wanted) {
                    LOG.warn("Port {} is in use, starting on port {} instead", wanted, port);
                    factory.setPort(port);
                }
                return;
            }
        }
        LOG.warn("No free port found between {} and {}, trying {} anyway", wanted, wanted + MAX_TRIES - 1, wanted);
    }

    private static boolean isFree(int port) {
        try (ServerSocket socket = new ServerSocket()) {
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(port));
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}

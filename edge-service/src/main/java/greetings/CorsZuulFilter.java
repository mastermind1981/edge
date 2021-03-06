package greetings;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.discovery.event.HeartbeatEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Profile("cors")
@Component
class CorsZuulFilter implements Filter {

    private final Map<String, List<ServiceInstance>> catalog = new ConcurrentHashMap<>();

    private final DiscoveryClient discoveryClient;

    @Autowired
    public CorsZuulFilter(DiscoveryClient discoveryClient) {
        this.discoveryClient = discoveryClient;
        this.refreshCatalog();
    }

    private boolean isClientAllowed(String origin) {
        if (StringUtils.hasText(origin)) {
            URI originUri = URI.create(origin);
            String match = originUri.getHost() + ':' + originUri.getPort();
            return this.catalog.keySet().stream().anyMatch(
                    serviceId -> this.catalog.get(serviceId)
                            .stream()
                            .map(si -> si.getHost() + ':' + si.getPort())
                            .anyMatch(hp -> hp.equalsIgnoreCase(match)));
        }
        return false;
    }

    @EventListener(HeartbeatEvent.class)
    public void onHeartbeatEvent(HeartbeatEvent event) {
        this.refreshCatalog();
    }

    // we don't want to constantly hit the registry, so proactively cache updates
    private void refreshCatalog() {
        discoveryClient.getServices()
                .forEach(svc -> this.catalog.put(svc, this.discoveryClient.getInstances(svc)));
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
        HttpServletResponse response = HttpServletResponse.class.cast(res);
        HttpServletRequest request = HttpServletRequest.class.cast(req);
        String originHeaderValue = request.getHeader(HttpHeaders.ORIGIN);
        boolean clientAllowed = isClientAllowed(originHeaderValue);
        if (clientAllowed) {
            response.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, originHeaderValue);
        }
        chain.doFilter(req, res);
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    @Override
    public void destroy() {
    }
}

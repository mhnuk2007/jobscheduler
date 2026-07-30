package dev.mhnuk2007.jobscheduler.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Set;

@Component
public class CallbackUrlValidatorImpl implements CallbackUrlValidator {
    private final Set<String> allowedDomains;
    private final Set<String> deniedHosts;

    public CallbackUrlValidatorImpl(
            @Value("#{'${callback.allowed-domains:}'.split(',')}") Set<String> allowedDomains,
            @Value("#{'${callback.denied-hosts:169.254.169.254,localhost,127.0.0.1}'.split(',')}") Set<String> deniedHosts
    ) {
        this.allowedDomains = Set.copyOf(allowedDomains.stream().filter(s -> !s.isBlank()).toList());
        this.deniedHosts = Set.copyOf(deniedHosts);
    }

    @Override
    public boolean isAllowed(String url) {
        try {
            URI uri = URI.create(url);
            if (!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme())) {
                return false;
            }
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return false;
            }
            if (!allowedDomains.isEmpty() && !allowedDomains.contains(host)) {
                return false;
            }
            if (deniedHosts.contains(host)) {
                return false;
            }
            InetAddress resolved = InetAddress.getByName(host);
            return !resolved.isLoopbackAddress()
                    && !resolved.isLinkLocalAddress()
                    && !resolved.isLinkLocalAddress()
                    && !resolved.isAnyLocalAddress();
        } catch (IllegalArgumentException | UnknownHostException ex) {
            return false;
        }

    }
}

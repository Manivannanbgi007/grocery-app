package com.grocery.gateway.ratelimit;

import org.springframework.cloud.gateway.support.ipresolver.RemoteAddressResolver;
import org.springframework.cloud.gateway.support.ipresolver.XForwardedRemoteAddressResolver;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

/**
 * Who a request counts against: the logged-in user ("user:4"), or the IP address of a guest ("ip:203.0.113.7").
 *
 * Behind a proxy (a Kubernetes Ingress) every request comes from the proxy's address, so all guests would share one
 * limit. With app.rate-limit.trusted-proxies = 1 the address is taken from X-Forwarded-For instead: the entry that
 * the trusted proxy added. Entries further left can be faked by the client, so they are never used.
 */
@Component
public class ClientKeyResolver {

    private final RemoteAddressResolver addressResolver;

    public ClientKeyResolver(RateLimitProperties properties) {
        this.addressResolver = properties.trustedProxies() > 0
                ? XForwardedRemoteAddressResolver.maxTrustedIndex(properties.trustedProxies())
                : new RemoteAddressResolver() { };   // the address of the TCP connection
    }

    public Mono<String> resolve(ServerWebExchange exchange) {
        return exchange.getPrincipal()
                .map(principal -> "user:" + principal.getName())   // the JWT subject = the user id
                .switchIfEmpty(Mono.fromSupplier(() -> "ip:" + clientIp(exchange)));
    }

    private String clientIp(ServerWebExchange exchange) {
        InetSocketAddress address = addressResolver.resolve(exchange);
        return address != null && address.getAddress() != null ? address.getAddress().getHostAddress() : "unknown";
    }
}

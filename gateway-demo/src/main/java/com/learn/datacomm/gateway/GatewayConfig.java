package com.learn.datacomm.gateway;

import com.learn.datacomm.gateway.filter.AccessLogFilter;
import com.learn.datacomm.gateway.filter.ApiKeyAuthFilter;
import com.learn.datacomm.gateway.filter.CorrelationIdFilter;
import com.learn.datacomm.gateway.filter.RateLimitFilter;
import com.learn.datacomm.gateway.ratelimit.FixedWindowRateLimiter;
import com.learn.datacomm.gateway.ratelimit.RateLimiter;
import com.learn.datacomm.gateway.ratelimit.TokenBucketRateLimiter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.servlet.Filter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
@ConfigurationProperties(prefix = "gateway")
public class GatewayConfig {

    /** prefix publik -> URL upstream, dari application.properties. */
    private Map<String, String> routes = new LinkedHashMap<>();
    private int connectTimeoutMs = 1000;
    private int readTimeoutMs = 2000;

    /**
     * Client yang terdaftar. Tiap client punya kebijakan rate limit sendiri -- di sini
     * sengaja berbeda algoritma supaya bisa dibandingkan:
     *   key-mobile  -> mobile-app  token bucket: burst 5, isi ulang 1 token/detik
     *   key-partner -> partner-x   fixed window: 5 request per jendela 5 detik
     */
    public static Map<String, String> apiKeys() {
        Map<String, String> keys = new HashMap<>();
        keys.put("key-mobile", "mobile-app");
        keys.put("key-partner", "partner-x");
        return keys;
    }

    @Bean
    public Map<String, RateLimiter> limiterByClient() {
        Map<String, RateLimiter> limiters = new HashMap<>();
        limiters.put("mobile-app", new TokenBucketRateLimiter(5, 1.0));
        limiters.put("partner-x", new FixedWindowRateLimiter(5, 5_000));
        return limiters;
    }

    @Bean
    public FilterRegistrationBean<Filter> correlationIdFilter() {
        return register(new CorrelationIdFilter(), 1);
    }

    @Bean
    public FilterRegistrationBean<Filter> accessLogFilter() {
        return register(new AccessLogFilter(), 2);
    }

    @Bean
    public FilterRegistrationBean<Filter> apiKeyAuthFilter() {
        return register(new ApiKeyAuthFilter(apiKeys()), 3);
    }

    @Bean
    public FilterRegistrationBean<Filter> rateLimitFilter(Map<String, RateLimiter> limiterByClient) {
        return register(new RateLimitFilter(limiterByClient), 4);
    }

    private static FilterRegistrationBean<Filter> register(Filter filter, int order) {
        FilterRegistrationBean<Filter> bean = new FilterRegistrationBean<>(filter);
        bean.addUrlPatterns("/api/*"); // hanya traffic publik; /_mock/* (upstream tiruan) tidak lewat sini
        bean.setOrder(order);
        return bean;
    }

    public Map<String, String> getRoutes() {
        return routes;
    }

    public void setRoutes(Map<String, String> routes) {
        this.routes = routes;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }
}

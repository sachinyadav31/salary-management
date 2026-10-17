package com.acme.salary.common.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;
import java.time.Duration;

/**
 * Serves the Angular build (copied into {@code classpath:/static/} by the Docker build) from the same origin
 * as the API: one deployable, no CORS.
 * <ul>
 *   <li>Controllers are matched before static resources, so {@code /api/**}, actuator and Swagger are unaffected.</li>
 *   <li>Extension-less paths that aren't files ({@code /employees/42}) fall back to {@code index.html} so the
 *       Angular router can handle deep links. Missing files ({@code /missing.js}) and unknown API paths stay 404.</li>
 *   <li>Angular's content-hashed bundles are cached for a year; everything else (notably {@code index.html})
 *       is revalidated, so a new deploy is picked up immediately.</li>
 * </ul>
 */
@Configuration
public class SpaWebConfig implements WebMvcConfigurer {

    private static final String STATIC = "classpath:/static/";

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Hashed build output (main-ABC123.js, chunk-XYZ.js, styles-….css, fonts in media/): immutable.
        CacheControl immutable = CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable();
        registry.addResourceHandler("/*.js", "/*.css")
                .addResourceLocations(STATIC)
                .setCacheControl(immutable);
        // A "/dir/**" pattern resolves only the "**" part against the location, so the location must include
        // the directory itself (static/media/, not static/).
        registry.addResourceHandler("/media/**")
                .addResourceLocations(STATIC + "media/")
                .setCacheControl(immutable);

        // Everything else, including the SPA fallback to index.html: always revalidate.
        registry.addResourceHandler("/**")
                .addResourceLocations(STATIC)
                .setCacheControl(CacheControl.noCache())
                .resourceChain(true)
                .addResolver(new SpaFallbackResolver());
    }

    static final class SpaFallbackResolver extends PathResourceResolver {

        @Override
        protected Resource getResource(String path, Resource location) throws IOException {
            // "" or "folder/" resolves to a directory, which "exists" but isn't a file to serve.
            boolean directory = path.isEmpty() || path.endsWith("/");
            Resource requested = location.createRelative(path);
            if (!directory && requested.exists() && requested.isReadable()) {
                return requested;
            }
            boolean apiOrInfrastructure = path.startsWith("api/") || path.startsWith("actuator/") || path.startsWith("v3/");
            boolean looksLikeFile = path.substring(path.lastIndexOf('/') + 1).contains(".");
            if (apiOrInfrastructure || looksLikeFile) {
                return null; // genuine 404
            }
            Resource index = location.createRelative("index.html");
            return index.exists() ? index : null;
        }
    }
}

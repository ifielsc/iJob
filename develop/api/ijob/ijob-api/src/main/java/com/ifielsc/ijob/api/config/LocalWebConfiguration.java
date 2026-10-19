package com.ifielsc.ijob.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Configuration
@ConditionalOnProperty(name = "app.web.enabled", havingValue = "true")
public class LocalWebConfiguration implements WebMvcConfigurer {
    private final Path directory;

    public LocalWebConfiguration(@Value("${app.web.directory}") String directory) {
        this.directory = Path.of(directory).toAbsolutePath().normalize();
        if (!Files.isRegularFile(this.directory.resolve("index.html"))) {
            throw new IllegalArgumentException("Web distribution index.html is missing");
        }
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/").setViewName("forward:/index.html");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations(directory.toUri().toString())
                .setCachePeriod(0)
                .resourceChain(false)
                .addResolver(
                        new PathResourceResolver() {
                            @Override
                            protected Resource getResource(String resourcePath, Resource location)
                                    throws IOException {
                                Path requested = directory.resolve(resourcePath).normalize();
                                if (!requested.startsWith(directory)
                                        || resourcePath.startsWith("api/")
                                        || resourcePath.startsWith("actuator/")) return null;
                                if (Files.exists(requested)
                                        && !requested
                                                .toRealPath()
                                                .startsWith(directory.toRealPath())) return null;
                                Resource actual = super.getResource(resourcePath, location);
                                if (actual != null && !Files.isDirectory(requested)) return actual;
                                if (!resourcePath.contains("."))
                                    return super.getResource("index.html", location);
                                return null;
                            }
                        });
    }
}

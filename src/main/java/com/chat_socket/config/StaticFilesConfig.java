package com.chat_socket.config;

import com.chat_socket.ApplicationYaml;
import com.chat_socket.constant.RouteApi;
import java.nio.file.Path;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class StaticFilesConfig implements WebMvcConfigurer {
    private final ApplicationYaml config;

    StaticFilesConfig(ApplicationYaml config) {
        this.config = config;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location =
                Path.of(config.uploadDir()).toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler(RouteApi.FILES + "/**").addResourceLocations(location);
    }
}

package com.joshfouchey.smsarchive.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import java.nio.file.Paths;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Value("${smsarchive.media.root:./media/messages}")
    private String mediaRoot;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Serve originals and thumbnails under /media/messages/** from the configured media root.
        String absolute = Paths.get(mediaRoot).toAbsolutePath().toString();
        if (!absolute.endsWith("/")) absolute = absolute + "/";
        registry.addResourceHandler("/media/messages/**")
                .addResourceLocations("file:" + absolute);
    }
}

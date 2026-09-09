package dev.olegz.vf.api.web.filter;

import java.util.List;

import dev.olegz.vf.api.web.ApiHeaders;
import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MvcConfigurer implements WebMvcConfigurer {
    /** Common path prefix shared by all VF API controllers. */
    static final String BASE_PATH = "/vf/";

    public void addInterceptors(InterceptorRegistry registry) {
        InterceptorRegistration registration = registry.addInterceptor(new ApiCallInterceptor());
        registration.addPathPatterns(List.of(BASE_PATH + "**"));
    }

    private static final String[] CORS_METHODS = {HttpMethod.GET.name(), HttpMethod.POST.name(), HttpMethod.PUT.name(), HttpMethod.DELETE.name()};
    private static final String[] CORS_HEADERS = {
        HttpHeaders.AUTHORIZATION,
        "Overwrite",
        HttpHeaders.CONTENT_TYPE,
        HttpHeaders.USER_AGENT,
        "passcode",
        "ACTIVATION_KEY",
        ApiHeaders.API_KEY,
        ApiHeaders.LAMBDA_API_KEY,
        ApiHeaders.PASSWORD
    };

    @Override
    public void addCorsMappings(@NonNull CorsRegistry registry) {
        registry.addMapping(BASE_PATH + "**")
            .allowedOrigins("*")
            .allowedMethods(CORS_METHODS)
            .allowedHeaders(CORS_HEADERS)
            .allowCredentials(false)
            .maxAge(86_400);
    }
}

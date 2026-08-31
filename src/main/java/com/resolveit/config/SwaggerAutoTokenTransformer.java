package com.resolveit.config;

import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springdoc.core.properties.SwaggerUiOAuthProperties;
import org.springdoc.core.providers.ObjectMapperProvider;
import org.springdoc.webmvc.ui.SwaggerIndexPageTransformer;
import org.springdoc.webmvc.ui.SwaggerWelcomeCommon;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.resource.ResourceTransformerChain;
import org.springframework.web.servlet.resource.TransformedResource;

import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Adds two conveniences to the Swagger page, both of them browser-side only:
 *
 *   1. Automatic JWT handling - the token returned by POST /api/auth/login is kept and
 *      replayed on later calls, so there is no Authorize button and nothing to copy.
 *   2. A search box and a single module/access dropdown for navigating the endpoint list.
 *
 * NEITHER GRANTS ANYTHING. This class rewrites the swagger-initializer.js that springdoc
 * serves; it never touches a controller, a filter or a security rule. The dropdown hides
 * and shows rows that are already on the page - picking "SUPER_ADMIN" does not make the
 * viewer a super admin, and an endpoint their role cannot reach still answers 403.
 *
 * Real enforcement stays entirely in SecurityConfig (URL rules), JwtAuthenticationFilter
 * (token validation) and IncidentAccessService (record ownership). Deleting this class
 * would change the Swagger page and nothing else about the API.
 *
 * The injected JavaScript lives in src/main/resources/swagger/ so it can be read and
 * edited as JavaScript rather than as an escaped Java string.
 */
@Configuration
public class SwaggerAutoTokenTransformer extends SwaggerIndexPageTransformer {

    /** Where springdoc's generated config object opens; interceptors go straight after. */
    private static final String ANCHOR = "SwaggerUIBundle({";

    private static final String UI_SCRIPT = "swagger/resolveit-ui.js";
    private static final String INTERCEPTORS_SCRIPT = "swagger/resolveit-interceptors.js";

    public SwaggerAutoTokenTransformer(SwaggerUiConfigProperties swaggerUiConfig,
                                       SwaggerUiOAuthProperties swaggerUiOAuthProperties,
                                       SwaggerWelcomeCommon swaggerWelcomeCommon,
                                       ObjectMapperProvider objectMapperProvider) {
        super(swaggerUiConfig, swaggerUiOAuthProperties, swaggerWelcomeCommon, objectMapperProvider);
    }

    @Override
    public Resource transform(HttpServletRequest request,
                              Resource resource,
                              ResourceTransformerChain chain) throws IOException {

        Resource transformed = super.transform(request, resource, chain);

        String filename = transformed.getFilename();
        if (filename == null || !filename.endsWith("swagger-initializer.js")) {
            return transformed;
        }

        String js = new String(transformed.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int at = js.indexOf(ANCHOR);
        if (at < 0) {
            // Swagger UI changed shape; serve the page exactly as springdoc built it.
            return transformed;
        }

        int insertAt = at + ANCHOR.length();
        String patched = readScript(UI_SCRIPT)
                + "\n"
                + js.substring(0, insertAt)
                + "\n" + readScript(INTERCEPTORS_SCRIPT)
                + js.substring(insertAt);

        return new TransformedResource(transformed, patched.getBytes(StandardCharsets.UTF_8));
    }

    private static String readScript(String path) throws IOException {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}

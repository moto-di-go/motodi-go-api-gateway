package com.motodigo.gateway.web;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SwaggerUiController {

	private static final String SWAGGER_UI_WEBJAR_VERSION = "5.32.0";
	private static final String SWAGGER_UI_BASE = "/webjars/swagger-ui/" + SWAGGER_UI_WEBJAR_VERSION;

	@GetMapping(value = "/api/v1/docs", produces = MediaType.TEXT_HTML_VALUE)
	public ResponseEntity<String> docs() {
		return ResponseEntity.ok("""
				<!doctype html>
				<html lang="fr">
				<head>
				  <meta charset="utf-8">
				  <meta name="viewport" content="width=device-width, initial-scale=1">
				  <title>MOTO'DI GO API v1 - Swagger UI</title>
				  <link rel="stylesheet" href="%s/swagger-ui.css">
				  <style>
				    body { margin: 0; background: #ffffff; }
				    .swagger-ui .topbar { display: none; }
				  </style>
				</head>
				<body>
				  <div id="swagger-ui"></div>
				  <script src="%s/swagger-ui-bundle.js"></script>
				  <script src="%s/swagger-ui-standalone-preset.js"></script>
				  <script>
				    window.onload = function () {
				      window.ui = SwaggerUIBundle({
				        url: "/api/v1/openapi",
				        dom_id: "#swagger-ui",
				        deepLinking: true,
				        presets: [
				          SwaggerUIBundle.presets.apis,
				          SwaggerUIStandalonePreset
				        ],
				        layout: "StandaloneLayout"
				      });
				    };
				  </script>
				</body>
				</html>
				""".formatted(SWAGGER_UI_BASE, SWAGGER_UI_BASE, SWAGGER_UI_BASE));
	}
}

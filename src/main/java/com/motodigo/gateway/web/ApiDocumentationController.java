package com.motodigo.gateway.web;

import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile({"local", "test"})
class ApiDocumentationController {

	@GetMapping(value = "/api/v1/docs", produces = MediaType.TEXT_HTML_VALUE)
	ResponseEntity<String> apiDocs() {
		return ResponseEntity.ok("""
				<!doctype html>
				<html lang="fr">
				<head>
				  <meta charset="utf-8">
				  <title>MOTO'DI GO API v1</title>
				</head>
				<body>
				  <main>
				    <h1>MOTO'DI GO API v1</h1>
				    <p>Documentation API de test pour les routes versionnees sous <code>/api/v1</code>.</p>
				    <p>Contrat OpenAPI JSON : <a href="/api/v1/openapi">/api/v1/openapi</a></p>
				  </main>
				</body>
				</html>
				""");
	}
}

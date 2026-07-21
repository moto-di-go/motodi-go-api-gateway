package com.motodigo.gateway.web;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OpenApiContractController {

	private static final MediaType OPENAPI_YAML = MediaType.valueOf("application/vnd.oai.openapi");

	@GetMapping(value = "/api/v1/openapi", produces = {
			"application/vnd.oai.openapi",
			"application/yaml",
			"text/yaml"
	})
	public ResponseEntity<Resource> openApi() {
		return ResponseEntity.ok()
				.contentType(OPENAPI_YAML)
				.body(new ClassPathResource("openapi/motodigo-openapi.yaml"));
	}
}

package com.motodigo.gateway.security;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class GatewayJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

	@Override
	public AbstractAuthenticationToken convert(Jwt jwt) {
		return new JwtAuthenticationToken(jwt, authorities(jwt), jwt.getSubject());
	}

	private static Collection<GrantedAuthority> authorities(Jwt jwt) {
		Set<GrantedAuthority> authorities = new LinkedHashSet<>();
		addClaimAuthorities(authorities, jwt.getClaimAsStringList("permissions"), "");
		addClaimAuthorities(authorities, jwt.getClaimAsStringList("roles"), "");
		addClaimAuthorities(authorities, jwt.getClaimAsStringList("roles"), "ROLE_");
		String activeProfileType = jwt.getClaimAsString("activeProfileType");
		if (StringUtils.hasText(activeProfileType)) {
			authorities.add(new SimpleGrantedAuthority("ACTIVE_PROFILE_" + activeProfileType));
		}
		return authorities;
	}

	private static void addClaimAuthorities(Set<GrantedAuthority> authorities, List<String> values, String prefix) {
		if (values == null) {
			return;
		}
		values.stream()
				.filter(StringUtils::hasText)
				.map(String::trim)
				.map(value -> prefix + value)
				.map(SimpleGrantedAuthority::new)
				.forEach(authorities::add);
	}
}

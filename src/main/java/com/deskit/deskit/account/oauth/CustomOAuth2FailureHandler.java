package com.deskit.deskit.account.oauth;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.log4j.Log4j2;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Log4j2
@Component
public class CustomOAuth2FailureHandler extends SimpleUrlAuthenticationFailureHandler {

	@Override
	public void onAuthenticationFailure(
			HttpServletRequest request,
			HttpServletResponse response,
			AuthenticationException exception
	) throws IOException, ServletException {
		String message = resolveMessage(exception);
		String escapedMessage = escapeForScript(message);

		response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		response.setContentType("text/html;charset=UTF-8");
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.getWriter().write(
				"<script>alert('" + escapedMessage + "'); window.location.href='http://localhost:5173/login';</script>"
		);
	}

	private String resolveMessage(AuthenticationException exception) {
		if (exception instanceof OAuth2AuthenticationException oauthEx) {
			OAuth2Error error = oauthEx.getError();
			if (error != null && error.getDescription() != null && !error.getDescription().isBlank()) {
				return error.getDescription();
			}
		}
		return "로그?�에 ?�패?�습?�다.";
	}

	private String escapeForScript(String message) {
		if (message == null) {
			return "";
		}
		return message
				.replace("\\", "\\\\")
				.replace("'", "\\'")
				.replace("\r", "")
				.replace("\n", "");
	}
}

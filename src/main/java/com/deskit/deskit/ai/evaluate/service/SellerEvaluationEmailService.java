package com.deskit.deskit.ai.evaluate.service;

import com.deskit.deskit.account.enums.SellerGradeEnum;
import com.deskit.deskit.account.oauth.CustomOAuth2User;
import com.deskit.deskit.admin.entity.Admin;
import com.deskit.deskit.admin.repository.AdminRepository;
import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import com.sendgrid.helpers.mail.objects.Personalization;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Log4j2
@Service
public class SellerEvaluationEmailService {

	private static final String ADMITTED_TEMPLATE_PATH = "email/doc/eval_result_admitted.html";
	private static final String REJECTED_TEMPLATE_PATH = "email/doc/eval_result_rejected.html";
	private static final Pattern TITLE_PATTERN = Pattern.compile("(?is)<title>\\s*(.*?)\\s*</title>");
	private static final Pattern BODY_PATTERN = Pattern.compile("(?is)<body[^>]*>(.*)</body>");
	private static final Pattern FIRST_NON_EMPTY_LINE = Pattern.compile("(?m)^\\s*\\S.*$");
	private final SendGrid sendGrid;
	private final AdminRepository adminRepository;
	private final String verifiedSenderEmail;

	public SellerEvaluationEmailService(
			@Value("${spring.sendgrid.api-key}") String apiKey,
			@Value("${spring.sendgrid.sender-email:dyniiyeyo@naver.com}") String verifiedSenderEmail,
			AdminRepository adminRepository
	) {
		this.sendGrid = new SendGrid(apiKey);
		this.adminRepository = adminRepository;
		this.verifiedSenderEmail = verifiedSenderEmail;
	}

	public void sendFinalResult(String toEmail, SellerGradeEnum grade, String adminComment, String sellerName, Integer totalScore) throws IOException {
		String senderEmail = resolveSenderEmail();
		Email from = new Email(verifiedSenderEmail);
		Email to = new Email(toEmail);

		EmailTemplate template = loadTemplate(grade);
		Map<String, String> replacements = buildReplacements(grade, adminComment, sellerName, totalScore);
		String subject = applyReplacements(template.subject(), replacements);
		String htmlBody = applyReplacements(template.body(), replacements);

		String plainBody = toPlainText(htmlBody);

		Mail mail = new Mail();
		mail.setFrom(from);
		mail.setReplyTo(new Email(senderEmail));
		mail.setSubject(subject);

		Personalization personalization = new Personalization();
		personalization.addTo(to);
		mail.addPersonalization(personalization);

		mail.addContent(new Content("text/plain", plainBody));
		mail.addContent(new Content("text/html", htmlBody));

		Request request = new Request();
		request.setMethod(Method.POST);
		request.setEndpoint("mail/send");
		request.setBody(mail.build());

		Response response;
		try {
			response = sendGrid.api(request);
		} catch (IOException ex) {
			log.error("SendGrid request failed", ex);
			throw ex;
		}
		if (response.getStatusCode() >= 400) {
			log.warn("SendGrid send failed: status={}, body={}", response.getStatusCode(), response.getBody());
			throw new IOException("sendgrid mail send failed");
		}
	}

	private String resolveSenderEmail() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

		if (authentication == null || !authentication.isAuthenticated()) {
			throw new IllegalStateException("admin authentication required");
		}
		Object principal = authentication.getPrincipal();
		if (!(principal instanceof CustomOAuth2User user)) {
			throw new IllegalStateException("admin principal not found");
		}
		String loginId = user.getUsername();
		if (loginId == null || loginId.isBlank()) {
			throw new IllegalStateException("admin login id not found");
		}
		Admin admin = adminRepository.findByLoginId(loginId);
		if (admin == null || admin.getLoginId() == null || admin.getLoginId().isBlank()) {
			throw new IllegalStateException("admin login id not found");
		}
		return admin.getLoginId();
	}

	private String toPlainText(String html) {
		if (html == null) return "";
		String text = html
				.replaceAll("(?is)<br\\s*/?>", "\n")
				.replaceAll("(?is)</p\\s*>", "\n\n")
				.replaceAll("(?is)<li\\s*>", " - ")
				.replaceAll("(?is)</li\\s*>", "\n")
				.replaceAll("(?s)<[^>]*>", "");
		return text.replaceAll("[\\t\\x0B\\f\\r]+", "").trim();
	}

	private EmailTemplate loadTemplate(SellerGradeEnum grade) throws IOException {
		String path = grade == SellerGradeEnum.REJECTED ? REJECTED_TEMPLATE_PATH : ADMITTED_TEMPLATE_PATH;
		String raw = readTemplate(path);
		String normalized = normalizeNewlines(stripBom(raw));
		return parseTemplate(normalized);
	}

	private String readTemplate(String path) throws IOException {
		ClassPathResource resource = new ClassPathResource(path);
		if (!resource.exists()) {
			throw new FileNotFoundException("Template not found in classpath: " + path);
		}

		try (InputStream is = resource.getInputStream()) {
			return new String(is.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private Map<String, String> buildReplacements(SellerGradeEnum grade, String adminComment, String sellerName, Integer totalScore) {
		String commentText = adminComment == null || adminComment.isBlank()
				? "추가 코멘트가 없습니다."
				: adminComment;
		Map<String, String> replacements = new LinkedHashMap<>();
		replacements.put("{{판매자명}}", safeValue(sellerName));
		replacements.put("{{총점}}", totalScore == null ? "" : totalScore.toString());
		replacements.put("{{배정그룹}}", resolveGroupLabel(grade));
		replacements.put("{{관리자종합의견}}", commentText);
		return replacements;
	}

	private String applyReplacements(String text, Map<String, String> replacements) {
		String result = text;
		for (Map.Entry<String, String> entry : replacements.entrySet()) {
			result = result.replace(entry.getKey(), entry.getValue());
		}
		return result;
	}

	private EmailTemplate parseTemplate(String content) {
		String subject = extractTitle(content);
		String body = extractBody(content);
		if (subject == null || subject.isBlank()) {
			String fallback = findFirstNonEmptyLine(content);
			subject = stripHtml(fallback).trim();
		}
		return new EmailTemplate(subject, body);
	}

	private String extractTitle(String content) {
		Matcher matcher = TITLE_PATTERN.matcher(content);
		if (matcher.find()) {
			return matcher.group(1).trim();
		}
		return "";
	}

	private String extractBody(String content) {
		Matcher matcher = BODY_PATTERN.matcher(content);
		if (matcher.find()) {
			return matcher.group(1).trim();
		}
		return content;
	}

	private String findFirstNonEmptyLine(String content) {
		Matcher matcher = FIRST_NON_EMPTY_LINE.matcher(content);
		if (matcher.find()) {
			return matcher.group();
		}
		return "";
	}

	private String normalizeNewlines(String value) {
		return value.replace("\r\n", "\n").replace("\r", "\n");
	}

	private String stripBom(String value) {
		if (value != null && value.startsWith("\uFEFF")) {
			return value.substring(1);
		}
		return value;
	}

	private String safeValue(String value) {
		return value == null ? "" : value;
	}

	private String resolveGroupLabel(SellerGradeEnum grade) {
		if (grade == null) {
			return "";
		}
		return switch (grade) {
			case A -> "공식 파트너";
			case B -> "인증 판매자";
			case C -> "신규 판매자";
			default -> grade.name();
		};
	}

	private String stripHtml(String value) {
		if (value == null) {
			return "";
		}
		return value.replaceAll("(?s)<[^>]*>", "");
	}

	private record EmailTemplate(String subject, String body) {
	}
}

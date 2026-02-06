package com.deskit.deskit.account.dto;

public class SellerManagerStatusResponse {
	private final Long id;
	private final String name;
	private final String email;
	private final String role;
	private final String status;

	public SellerManagerStatusResponse(Long id, String name, String email, String role, String status) {
		this.id = id;
		this.name = name;
		this.email = email;
		this.role = role;
		this.status = status;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getEmail() {
		return email;
	}

	public String getRole() {
		return role;
	}

	public String getStatus() {
		return status;
	}
}

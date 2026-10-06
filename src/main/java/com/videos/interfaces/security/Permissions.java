package com.videos.interfaces.security;

/**
 * Method-security expressions used by the controller. The permissions are granted to roles in
 * auth-service and arrive in the access token; this service defines no roles of its own.
 */
public final class Permissions {

	public static final String VIDEOS_MANAGE = "videos:manage";

	public static final String READ = "hasAuthority('videos:read')";
	public static final String WRITE = "hasAuthority('videos:write')";
	public static final String MANAGE = "hasAuthority('" + VIDEOS_MANAGE + "')";

	private Permissions() {
	}
}

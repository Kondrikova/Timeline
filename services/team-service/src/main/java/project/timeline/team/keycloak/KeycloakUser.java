package project.timeline.team.keycloak;

/**
 * Пользователь из каталога Keycloak (без сервисных учёток).
 */
public record KeycloakUser(
		String id,
		String username,
		String firstName,
		String lastName,
		String email,
		boolean enabled) {

	public String displayName() {
		if (firstName != null && !firstName.isBlank() && lastName != null && !lastName.isBlank()) {
			return firstName + " " + lastName;
		}
		if (firstName != null && !firstName.isBlank()) {
			return firstName;
		}
		if (username != null && !username.isBlank()) {
			return username;
		}
		return id;
	}
}

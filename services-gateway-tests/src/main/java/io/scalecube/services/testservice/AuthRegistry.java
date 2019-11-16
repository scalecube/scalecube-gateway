package io.scalecube.services.testservice;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** So called "guess username" authentication. All preconfigured users can be authenticated. */
public class AuthRegistry {

  public static final String SESSION_ID = "SESSION_ID";

  /** Preconfigured userName-s that are allowed to be authenticated */
  private final Set<String> allowedUsers;

  private ConcurrentMap<String, String> loggedInUsers = new ConcurrentHashMap<>();

  public AuthRegistry(Set<String> allowedUsers) {
    this.allowedUsers = allowedUsers;
  }

  public Optional<String> getAuth(String sessionId) {
    return Optional.ofNullable(loggedInUsers.get(sessionId));
  }

  public Optional<String> addAuth(String sessionId, String auth) {
    if (allowedUsers.contains(auth)) {
      loggedInUsers.putIfAbsent(sessionId, auth);
      return Optional.of(auth);
    } else {
      System.err.println("User not in list of ALLOWED: " + auth);
    }
    return Optional.empty();
  }

  public String removeAuth(String sessionId) {
    return loggedInUsers.remove(sessionId);
  }
}

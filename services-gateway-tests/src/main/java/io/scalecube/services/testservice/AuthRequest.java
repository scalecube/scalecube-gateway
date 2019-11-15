package io.scalecube.services.testservice;

public class AuthRequest {
  private String username;

  public AuthRequest() {}

  public AuthRequest(String username) {
    this.username = username;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  @Override
  public String toString() {
    return "AuthRequest{" + "username='" + username + '\'' + '}';
  }
}

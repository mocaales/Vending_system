package si.vending.monitoring.core;

import org.springframework.http.HttpStatus;

/** A required dependency (database, another service) cannot be reached. */
public class DependencyUnavailableException extends ApiException {

  public DependencyUnavailableException(String message) {
    super(HttpStatus.SERVICE_UNAVAILABLE, "DEPENDENCY_UNAVAILABLE", message);
  }
}

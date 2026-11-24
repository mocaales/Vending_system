package si.vending.sales.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;

/**
 * Every container receives {@code <CALLER>_SERVICE_TOKEN} env vars (the tokens it accepts from its
 * callers). Spring relaxed binding maps {@code PRODUCT_SERVICE_TOKEN} onto the key {@code
 * product.service-token}, and an OS env var beats {@code application.yml}. A
 * {@code @ConfigurationProperties} prefix that equals a caller name is therefore hijackable: the
 * service would send another service's own token. Client settings must use a prefix such as {@code
 * product-client}.
 */
class ConfigurationPrefixGuardTest {

  private static final Set<String> CALLER_NAMES =
      Set.of("GATEWAY", "MACHINE", "MONITORING", "SALES", "INVENTORY", "PRODUCT");

  @Test
  void noConfigurationPropertiesPrefixCollidesWithAServiceTokenEnvVar() throws Exception {
    ClassPathScanningCandidateComponentProvider scanner =
        new ClassPathScanningCandidateComponentProvider(false) {
          @Override
          protected boolean isCandidateComponent(AnnotatedBeanDefinition definition) {
            return true;
          }
        };
    scanner.addIncludeFilter(new AnnotationTypeFilter(ConfigurationProperties.class));

    List<String> offenders = new ArrayList<>();
    int found = 0;
    for (BeanDefinition candidate : scanner.findCandidateComponents("si.vending.sales")) {
      Class<?> type = Class.forName(candidate.getBeanClassName());
      ConfigurationProperties annotation =
          AnnotatedElementUtils.findMergedAnnotation(type, ConfigurationProperties.class);
      found++;
      String env = annotation.prefix().replaceAll("[^A-Za-z0-9]", "_").toUpperCase(Locale.ROOT);
      for (String caller : CALLER_NAMES) {
        if (env.equals(caller) || env.startsWith(caller + "_SERVICE")) {
          offenders.add(
              type.getSimpleName()
                  + " prefix '"
                  + annotation.prefix()
                  + "' collides with "
                  + caller
                  + "_SERVICE_TOKEN");
        }
      }
    }

    assertThat(found).as("scan must find the @ConfigurationProperties classes").isPositive();
    assertThat(offenders).as("hijackable @ConfigurationProperties prefixes").isEmpty();
  }
}

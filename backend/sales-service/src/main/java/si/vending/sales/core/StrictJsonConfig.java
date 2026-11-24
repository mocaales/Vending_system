package si.vending.sales.core;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.type.LogicalType;

/**
 * A JSON number or boolean is never accepted where the contract says string, and a string or
 * fraction never where it says integer.
 */
@Configuration
public class StrictJsonConfig {

  @Bean
  JsonMapperBuilderCustomizer strictStrings() {
    return builder ->
        builder
            .enable(EnumFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
            .withCoercionConfig(
                LogicalType.Textual,
                config -> {
                  config.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail);
                  config.setCoercion(CoercionInputShape.Float, CoercionAction.Fail);
                  config.setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
                })
            .withCoercionConfig(
                LogicalType.Integer,
                config -> {
                  config.setCoercion(CoercionInputShape.Float, CoercionAction.Fail);
                  config.setCoercion(CoercionInputShape.String, CoercionAction.Fail);
                });
  }
}

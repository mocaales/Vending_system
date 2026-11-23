package si.vending.monitoring.core;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Query parameters {@code limit} (default 50, 1..200) and {@code offset} (default 0, >= 0). */
public class PageParams {

  @Min(1)
  @Max(200)
  private int limit = 50;

  @Min(0)
  private int offset = 0;

  public int limit() {
    return limit;
  }

  public int offset() {
    return offset;
  }

  public void setLimit(int limit) {
    this.limit = limit;
  }

  public void setOffset(int offset) {
    this.offset = offset;
  }
}

package si.vending.machine.machine;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** limit/offset paging (offset need not be a multiple of limit). */
final class OffsetPageRequest implements Pageable {

  private final int limit;
  private final long offset;
  private final Sort sort;

  OffsetPageRequest(int limit, long offset, Sort sort) {
    this.limit = limit;
    this.offset = offset;
    this.sort = sort;
  }

  @Override
  public int getPageNumber() {
    return (int) (offset / limit);
  }

  @Override
  public int getPageSize() {
    return limit;
  }

  @Override
  public long getOffset() {
    return offset;
  }

  @Override
  public Sort getSort() {
    return sort;
  }

  @Override
  public Pageable next() {
    return new OffsetPageRequest(limit, offset + limit, sort);
  }

  @Override
  public Pageable previousOrFirst() {
    return new OffsetPageRequest(limit, Math.max(0, offset - limit), sort);
  }

  @Override
  public Pageable first() {
    return new OffsetPageRequest(limit, 0, sort);
  }

  @Override
  public Pageable withPage(int pageNumber) {
    return new OffsetPageRequest(limit, (long) pageNumber * limit, sort);
  }

  @Override
  public boolean hasPrevious() {
    return offset > 0;
  }
}

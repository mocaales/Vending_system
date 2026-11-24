package si.vending.sales.sales;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import si.vending.sales.core.ApiException;
import si.vending.sales.core.Page;
import si.vending.sales.sales.ClosingDtos.ClosingIn;
import si.vending.sales.sales.ClosingDtos.ClosingOut;
import si.vending.sales.sales.ClosingDtos.ClosingStatus;
import si.vending.sales.sales.ClosingDtos.ClosingUpdate;
import si.vending.sales.sales.ClosingDtos.ClosingsDeleted;
import si.vending.sales.sales.ClosingRepository.Filter;
import si.vending.sales.sales.ClosingRepository.Row;
import si.vending.sales.sales.ClosingRepository.Snapshot;
import si.vending.sales.sales.SaleDtos.Totals;

/**
 * Daily closings. Never calls another service: a machine without sales is an empty DRAFT. The
 * unique constraint decides concurrent creates; a row lock ({@code FOR UPDATE}) serialises the
 * writers of one closing.
 */
@Service
public class ClosingService {

  private final ClosingRepository closings;

  public ClosingService(ClosingRepository closings) {
    this.closings = closings;
  }

  @Transactional
  public ClosingOut create(ClosingIn in, UUID operatorId) {
    LocalDate date = parseDate(in.date());
    if (!date.equals(LocalDate.now(ZoneOffset.UTC).minusDays(1))) {
      throw ApiException.invalidField("date", "must be yesterday (UTC).");
    }
    Snapshot snapshot = closings.snapshot(date, in.machineId());
    UUID id = UUID.randomUUID();
    Instant now = now();
    String note = in.note() == null ? "" : in.note();
    if (!closings.insertDraft(
        id, date, in.machineId(), note, snapshot.openReconciliations(), operatorId, now)) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "CLOSING_EXISTS",
          "A closing for this date and machine already exists.");
    }
    closings.insertTotals(id, snapshot.totals());
    return out(closings.find(id).orElseThrow(), snapshot.totals());
  }

  @Transactional(readOnly = true)
  public ClosingOut get(UUID id) {
    Row row = closings.find(id).orElseThrow(ClosingService::notFound);
    return out(row, closings.totalsOf(id));
  }

  @Transactional(readOnly = true)
  public Page<ClosingOut> list(
      LocalDate from, LocalDate to, UUID machineId, ClosingStatus status, int limit, int offset) {
    checkBound("from", from);
    checkBound("to", to);
    if (from != null && to != null && from.isAfter(to)) {
      throw ApiException.invalidField("from", "must not be after to.");
    }
    Filter filter = new Filter(from, to, machineId, status);
    long total = closings.count(filter);
    List<Row> rows = closings.page(filter, limit, offset);
    Map<UUID, List<Totals>> totals = closings.totalsOf(rows.stream().map(Row::id).toList());
    List<ClosingOut> items =
        rows.stream().map(r -> out(r, totals.getOrDefault(r.id(), List.of()))).toList();
    return new Page<>(items, total, limit, offset);
  }

  /**
   * DRAFT: recompute, replace the note, optionally confirm (only without open reconciliations; a
   * refused confirmation rolls everything back). CONFIRMED: the identical request is 200, anything
   * else 409.
   */
  @Transactional
  public ClosingOut update(UUID id, ClosingUpdate in, UUID operatorId) {
    Row row = closings.findForUpdate(id).orElseThrow(ClosingService::notFound);
    if (row.status() == ClosingStatus.CONFIRMED) {
      boolean same =
          in.status() == ClosingStatus.CONFIRMED
              && (in.note() == null || in.note().equals(row.note()));
      if (!same) {
        throw confirmed();
      }
      return out(row, closings.totalsOf(id));
    }
    Snapshot snapshot = closings.snapshot(row.date(), row.machineId());
    String note = in.note() == null ? row.note() : in.note();
    boolean confirm = in.status() == ClosingStatus.CONFIRMED;
    if (confirm && snapshot.openReconciliations() > 0) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "CLOSING_HAS_OPEN_RECONCILIATION",
          "Sales of this closing still need reconciliation.");
    }
    closings.deleteTotals(id);
    closings.insertTotals(id, snapshot.totals());
    Instant now = now();
    if (confirm) {
      closings.confirm(id, note, snapshot.openReconciliations(), operatorId, now);
    } else {
      closings.updateDraft(id, note, snapshot.openReconciliations(), now);
    }
    return out(closings.find(id).orElseThrow(), snapshot.totals());
  }

  @Transactional
  public void delete(UUID id) {
    if (closings.deleteDraft(id) == 1) {
      return;
    }
    // not deleted: unknown, or confirmed (also when it was confirmed while this call waited)
    closings.find(id).orElseThrow(ClosingService::notFound);
    throw confirmed();
  }

  @Transactional
  public ClosingsDeleted deleteDrafts(LocalDate date) {
    return new ClosingsDeleted(closings.deleteDraftsOf(date));
  }

  private static final LocalDate BOUND_MIN = LocalDate.of(2020, 1, 1);
  private static final LocalDate BOUND_MAX = LocalDate.of(2100, 1, 1);

  private static void checkBound(String field, LocalDate value) {
    if (value != null && (value.isBefore(BOUND_MIN) || value.isAfter(BOUND_MAX))) {
      throw ApiException.invalidField(field, "must be between 2020-01-01 and 2100-01-01.");
    }
  }

  private static LocalDate parseDate(String value) {
    try {
      return LocalDate.parse(value);
    } catch (DateTimeParseException e) {
      throw ApiException.invalidField("date", "must be a date as YYYY-MM-DD.");
    }
  }

  private static Instant now() {
    return Instant.now().truncatedTo(ChronoUnit.MICROS);
  }

  private static ClosingOut out(Row r, List<Totals> totals) {
    return new ClosingOut(
        r.id(),
        r.date(),
        r.machineId(),
        r.status(),
        r.note(),
        totals,
        r.openReconciliations(),
        r.createdAt(),
        r.createdBy(),
        r.updatedAt(),
        r.confirmedAt(),
        r.confirmedBy());
  }

  private static ApiException notFound() {
    return new ApiException(HttpStatus.NOT_FOUND, "CLOSING_NOT_FOUND", "Closing not found.");
  }

  private static ApiException confirmed() {
    return new ApiException(
        HttpStatus.CONFLICT, "CLOSING_CONFIRMED", "The closing is confirmed and cannot change.");
  }
}

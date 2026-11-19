package si.vending.machine.core;

import java.util.List;

/** List response model: {@code {items, total, limit, offset}}. */
public record Page<T>(List<T> items, long total, int limit, int offset) {}

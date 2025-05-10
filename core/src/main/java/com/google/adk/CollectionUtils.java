package com.google.adk;

import com.google.common.collect.Iterables;

/** Frequently used code snippets for collections. */
public final class CollectionUtils {

  /**
   * Checks if the given iterable is null or empty.
   *
   * @param iterable the iterable to check
   * @return true if the iterable is null or empty, false otherwise
   */
  public static <T> boolean isNullOrEmpty(Iterable<T> iterable) {
    return iterable == null || Iterables.isEmpty(iterable);
  }

  private CollectionUtils() {}
}

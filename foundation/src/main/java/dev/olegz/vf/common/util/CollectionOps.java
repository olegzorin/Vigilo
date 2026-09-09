package dev.olegz.vf.common.util;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * Operations for processing collections without creating a Stream pipeline.
 * Prefer these methods for a single operation when stream chaining offers no benefit,
 * particularly when the input collection may be {@code null}. Each method defines its
 * own handling of null inputs, elements, and results.
 */
public class CollectionOps {
    /**
     * Search an element that satisfies the specified condition.
     * @param col    input
     * @param filter condition
     * @param <T>    input type
     * @return an element of the collection, that satisfies the condition, or null,
     * if the collection is empty or there are no such element in the collection.
     */
    public static <T> T findAny(final Collection<T> col, final Predicate<T> filter) {
        if (col != null) {
            for (T t : col) {
                if ((t != null) && filter.test(t)) return t;
            }
        }
        return null;
    }

    /**
     * Check if the collection contains an element that satisfies the specified condition.
     * @param col    input
     * @param filter condition
     * @param <T>    input type
     * @return true if the collection contains an element that satisfies the condition,
     * or false, if the collection is empty or there are no such elements in the collection.
     */
    public static <T> boolean anyMatch(final Collection<T> col, final Predicate<T> filter) {
        if (col != null) {
            for (T t : col) {
                if ((t != null) && filter.test(t)) return true;
            }
        }
        return false;
    }

    public static <T> int sumIntValues(final Collection<T> col, final ToIntFunction<T> func) {
        int res = 0;
        if (col != null) {
            for (T t : col) {
                if (t != null) res += func.applyAsInt(t);
            }
        }
        return res;
    }

    /**
     * Collect non-null mapping results of non-null input collection elements to a list
     * @param input  input
     * @param mapper one-to-one mapping function
     * @param <V>    input type
     * @param <T>    output type
     * @return non-empty list or null if nothing to collect
     */
    public static <V, T> List<T> map(final Collection<V> input, final Function<V, T> mapper) {
        if ((input == null) || input.isEmpty()) return null;

        ArrayList<T> res = new ArrayList<>(input.size());
        input.forEach(v -> {
            T t;
            if ((v != null) && ((t = mapper.apply(v)) != null)) res.add(t);
        });

        return res.isEmpty() ? null : res;
    }

    public static <T, K, V> Map<K, List<V>> group(final Collection<T> input, final Function<T, K> keyMapper, final Function<T, V> valueMapper) {
        HashMap<K, List<V>> map = new HashMap<>(input.size());
        input.forEach(t -> {
            K k;
            V v;
            if ((t != null) && ((k = keyMapper.apply(t)) != null) && ((v = valueMapper.apply(t)) != null)) {
                map.computeIfAbsent(k, _ -> new ArrayList<>()).add(v);
            }
        });

        return map;
    }

    public static <K, V> Map<K, V> toMap(final Collection<V> input, final Function<V, K> keyMapper) {
        if ((input == null) || input.isEmpty()) return Map.of();

        HashMap<K, V> map = new HashMap<>(input.size());
        input.forEach(v -> {
            K k;
            if ((v != null) && ((k = keyMapper.apply(v)) != null)) {
                map.put(k, v);
            }
        });
        return map;
    }

    /**
     * Collect non-null mapping results of non-null input collection elements to a list
     * @param input  input
     * @param mapper one-to-many mapping function
     * @param <V>    input type
     * @param <T>    output type
     * @return non-empty list or null if nothing to collect
     */
    public static <V, T> List<T> flatMap(final Collection<V> input, final Function<V, Collection<T>> mapper) {
        if ((input == null) || input.isEmpty()) return null;

        ArrayList<T> res = new ArrayList<>(input.size());
        input.forEach(v -> {
            Collection<T> t;
            if ((v != null) && ((t = mapper.apply(v)) != null)) res.addAll(t);
        });

        return res.isEmpty() ? null : res;
    }

}

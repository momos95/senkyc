package sn.senkyc.api.platform.pagination;

import java.util.List;
import java.util.function.Function;

/** Page de résultats (pagination par décalage). */
public record PageResult<T>(List<T> items, int page, int size, long total) {

    public PageResult {
        items = List.copyOf(items);
    }

    public <R> PageResult<R> map(Function<? super T, ? extends R> mapper) {
        return new PageResult<>(items.stream().<R>map(mapper).toList(), page, size, total);
    }
}

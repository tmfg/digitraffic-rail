package fi.livi.rata.avoindata.common.utils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class PreviousAndNext<T> implements Serializable {

    public final T previous;

    public final T current;

    public final T next;

    public PreviousAndNext(final T previous, final T current, final T next) {
        this.previous = previous;
        this.current = current;
        this.next = next;
    }

    public static <T> List<PreviousAndNext<T>> build(final Collection<T> list) {
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }

        final List<PreviousAndNext<T>> output = new ArrayList<PreviousAndNext<T>>(list.size());

        T previous = null;
        T current = null;

        for (final T next : list) {
            if (previous != null || current != null) {
                output.add(new PreviousAndNext<T>(previous, current, next));
            }

            previous = current;
            current = next;
        }

        output.add(new PreviousAndNext<T>(previous, current, null));

        return output;
    }
}

package loop.lang;

import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;

import loop.LoopExecutionException;

/**
 * @author dhanji@gmail.com (Dhanji R. Prasanna)
 */
public class ImmutableList extends ArrayList implements Immutable {

  private static final long serialVersionUID = 1L;

  public ImmutableList(final Collection<?> collection) {
    final IdentityHashMap<Object, Object> cyclesCheck = new IdentityHashMap<Object, Object>();
    cyclesCheck.put(collection, this);

    this.deepCopy(collection, cyclesCheck);
  }

  public ImmutableList(final Collection<?> collection, final IdentityHashMap<Object, Object> cyclesCheck) {
    this.deepCopy(collection, cyclesCheck);
  }

  @SuppressWarnings("unchecked")
  private void deepCopy(final Collection<?> collection, final IdentityHashMap<Object, Object> cyclesCheck) {
    for (Object value : collection) {

      // Make immutable copy of value if necessary.
      if (value instanceof Map) {
        final Object previouslyCopied = cyclesCheck.get(value);
        if (previouslyCopied != null) {
          value = previouslyCopied;
        } else {
          final ImmutableLoopObject copied = new ImmutableLoopObject(LoopClass.IMMUTABLE_MAP,
              (Map<Object, Object>) value, cyclesCheck);
          cyclesCheck.put(value, copied);
          value = copied;
        }
      } else if (value instanceof Collection) {
        final Object previouslyCopied = cyclesCheck.get(value);

        if (previouslyCopied != null) {
          value = previouslyCopied;
        } else {
          final ImmutableList copied = new ImmutableList((Collection<?>) value, cyclesCheck);
          cyclesCheck.put(value, copied);
          value = copied;
        }
      }

      // Ensure immutability.
      if (!ImmutableLoopObject.isImmutable(value)) {
        throw new LoopExecutionException("Cannot add a mutable value to an immutable object");
      }

      super.add(value);
    }
  }

  @Override
  public Object set(final int i, final Object o) {
    throw new LoopExecutionException(ImmutableLoopObject.IMMUTABILITY_ERROR);
  }

  @Override
  public boolean add(final Object o) {
    throw new LoopExecutionException(ImmutableLoopObject.IMMUTABILITY_ERROR);
  }

  @Override
  public void add(final int i, final Object o) {
    throw new LoopExecutionException(ImmutableLoopObject.IMMUTABILITY_ERROR);
  }

  @Override
  public Object remove(final int i) {
    throw new LoopExecutionException(ImmutableLoopObject.IMMUTABILITY_ERROR);
  }

  @Override
  public boolean remove(final Object o) {
    throw new LoopExecutionException(ImmutableLoopObject.IMMUTABILITY_ERROR);
  }

  @Override
  public void clear() {
    throw new LoopExecutionException(ImmutableLoopObject.IMMUTABILITY_ERROR);
  }

  @Override
  public boolean addAll(final Collection collection) {
    throw new LoopExecutionException(ImmutableLoopObject.IMMUTABILITY_ERROR);
  }

  @Override
  public boolean addAll(final int i, final Collection collection) {
    throw new LoopExecutionException(ImmutableLoopObject.IMMUTABILITY_ERROR);
  }

  @Override
  protected void removeRange(final int i, final int i1) {
    throw new LoopExecutionException(ImmutableLoopObject.IMMUTABILITY_ERROR);
  }

  @Override
  public boolean removeAll(final Collection objects) {
    throw new LoopExecutionException(ImmutableLoopObject.IMMUTABILITY_ERROR);
  }
}

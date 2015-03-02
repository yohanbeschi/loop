package loop.lang;

import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;

import loop.LoopExecutionException;

/**
 * The root object of all immutable object instances in loop. Actually this is the Java class that backs all instances
 * of immutable loop types.
 *
 * @author dhanji@gmail.com (Dhanji R. Prasanna)
 */
public class ImmutableLoopObject extends LoopObject implements Immutable {
  private static final long serialVersionUID = 1L;

  static final String IMMUTABILITY_ERROR = "Illegal attempt to create an object oriented language!";

  public ImmutableLoopObject(final LoopClass type, final Map<Object, Object> source) {
    super(type);

    final IdentityHashMap<Object, Object> cyclesCheck = new IdentityHashMap<Object, Object>();
    cyclesCheck.put(source, this);

    this.deepCopy(cyclesCheck, source);
  }

  public ImmutableLoopObject(final LoopClass type, final Map<Object, Object> source,
      final IdentityHashMap<Object, Object> cyclesCheck) {
    super(type);
    this.deepCopy(cyclesCheck, source);
  }

  @SuppressWarnings("unchecked")
  private void deepCopy(final IdentityHashMap<Object, Object> cyclesCheck, final Map<Object, Object> source) {
    for (final Map.Entry<Object, Object> entry : source.entrySet()) {
      Object value = entry.getValue();

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

      super.put(entry.getKey(), value);
    }
  }

  static boolean isImmutable(final Object value) {
    return value instanceof Immutable || value instanceof String || value instanceof Number;
  }

  @Override
  public Object put(final Object o, final Object o1) {
    throw new LoopExecutionException(ImmutableLoopObject.IMMUTABILITY_ERROR);
  }

  @Override
  public void putAll(final Map<?, ?> map) {
    throw new LoopExecutionException(ImmutableLoopObject.IMMUTABILITY_ERROR);
  }

  @Override
  public void clear() {
    throw new LoopExecutionException(ImmutableLoopObject.IMMUTABILITY_ERROR);
  }

  @Override
  public Object remove(final Object o) {
    throw new LoopExecutionException(ImmutableLoopObject.IMMUTABILITY_ERROR);
  }
}

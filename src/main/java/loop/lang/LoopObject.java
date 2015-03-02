package loop.lang;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import loop.LoopExecutionException;

/**
 * The root object of all object instances in loop. Actually this is the Java class that backs all instances of all loop
 * types.
 *
 * @author dhanji@gmail.com (Dhanji R. Prasanna)
 */
public class LoopObject extends HashMap<Object, Object> {
  private static final long serialVersionUID = 1L;

  private static final String NO_DESTROY = "This ain't Javascript! Can't mutate objects destructively.";
  private final LoopClass type;

  public LoopObject(final LoopClass type) {
    this.type = type;
  }

  public LoopClass getType() {
    return this.type;
  }

  @Override
  public Object remove(final Object o) {
    throw new LoopExecutionException(LoopObject.NO_DESTROY);
  }

  @Override
  public void clear() {
    throw new LoopExecutionException(LoopObject.NO_DESTROY);
  }

  @Override
  public Set<Object> keySet() {
    return Collections.unmodifiableSet(super.keySet());
  }

  @Override
  public Collection<Object> values() {
    return Collections.unmodifiableCollection(super.values());
  }

  @Override
  public Set<Map.Entry<Object, Object>> entrySet() {
    return Collections.unmodifiableSet(super.entrySet());
  }

  public ImmutableLoopObject immutize() {
    return new ImmutableLoopObject(this.type, this);
  }

  public LoopObject copy() {
    return LoopObject.copy(new LoopObject(this.type), this);
  }

  @SuppressWarnings("unchecked")
  private static <T> T copy(final Map<Object, Object> to, final Map<Object, Object> from) {
    for (final Map.Entry<Object, Object> entry : from.entrySet()) {
      Object value = entry.getValue();

      // Make mutable if necessary.
      if (value instanceof Collection) {
        value = LoopObject.copy((Collection) value);
      } else if (value instanceof Map) {
        final Map toCopy = (Map) value;
        value = LoopObject.copy(new HashMap<Object, Object>(toCopy.size()), toCopy);
      }

      to.put(entry.getKey(), value);
    }

    return (T) to;
  }

  @SuppressWarnings("unchecked")
  public static Collection copy(final Collection value) {
    Collection<Object> copy;
    if (value instanceof Set) {
      copy = new HashSet<Object>(value.size());
    } else {
      copy = new ArrayList<Object>(value.size());
    }

    for (Object item : value) {
      if (item instanceof Collection) {
        item = LoopObject.copy((Collection) item);
      } else if (item instanceof Map) {
        final Map<Object, Object> toCopy = (Map<Object, Object>) item;
        item = LoopObject.copy(new HashMap<Object, Object>(toCopy.size()), toCopy);
      }

      copy.add(item);
    }

    return copy;
  }
}

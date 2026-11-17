
import java.util.Objects;

/**
 * Represents an I/O redirection attached to a command stage in a pipeline.
 */
public class Redirection {

    public enum Type {
        INPUT,              // < target
        OUTPUT_OVERWRITE,   // > target
        OUTPUT_APPEND,      // >> target
        ERROR_OVERWRITE,    // 2> target
        ERROR_APPEND,       // 2>> target
        ERROR_TO_OUTPUT,    // 2>&1
        ALL_OVERWRITE,      // &> target
        ALL_APPEND          // &>> target
    }

    private final Type type;
    private final String target;

    public Redirection(Type type, String target) {
        this.type = Objects.requireNonNull(type, "Redirection Type cannot be null");
        this.target = target;
    }

    public Type getType() {
        return type;
    }

    public String getTarget() {
        return target;
    }

    @Override
    public String toString() {
        return "Redirection{" + type + " -> '" + target + "'}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Redirection that)) return false;
        return type == that.type && Objects.equals(target, that.target);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, target);
    }
}

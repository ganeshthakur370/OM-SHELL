import java.util.Objects;



public class Redirection {



    public enum Type {

        INPUT,

        OUTPUT_OVERWRITE,

        OUTPUT_APPEND,

        ERROR_OVERWRITE,

        ERROR_APPEND,

        ERROR_TO_OUTPUT,

        OUTPUT_TO_ERROR,

        ALL_OVERWRITE,

        ALL_APPEND,

        HERE_STRING,

        HERE_DOC,

        CLOBBER

    }



    private final Type type;

    private final String target;



    public Redirection(Type type, String target) {

        this.type = Objects.requireNonNull(type, "Redirection Type cannot be null");

        this.target = target != null ? target : "";

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

import java.util.Objects;



public class Token {

    private final TokenType type;

    private final String value;



    public Token(TokenType type, String value) {

        this.type = Objects.requireNonNull(type, "TokenType cannot be null");

        this.value = value == null ? "" : value;

    }



    public TokenType getType() {

        return type;

    }



    public String getValue() {

        return value;

    }



    @Override

    public String toString() {

        return "Token{" + type + ", '" + value + "'}";

    }



    @Override

    public boolean equals(Object o) {

        if (this == o) return true;

        if (!(o instanceof Token token)) return false;

        return type == token.type && Objects.equals(value, token.value);

    }



    @Override

    public int hashCode() {

        return Objects.hash(type, value);

    }

}

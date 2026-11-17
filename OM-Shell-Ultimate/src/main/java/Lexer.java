
import java.util.ArrayList;
import java.util.List;

/**
 * Tokenizes raw shell input into structured tokens.
 * Handles single quotes, double quotes, backslash escapes,
 * redirection operators, pipelines, and chaining symbols.
 */
public class Lexer {

    private final String input;
    private final int length;
    private int cursor;

    public Lexer(String input) {
        this.input = input != null ? input : "";
        this.length = this.input.length();
        this.cursor = 0;
    }

    /**
     * Scans the input string and produces a list of tokens.
     *
     * @return List of tokens
     * @throws IllegalArgumentException if an unclosed quote is encountered
     */
    public List<Token> tokenize() {
        List<Token> tokens = new ArrayList<>();
        StringBuilder word = new StringBuilder();
        char activeQuote = 0;
        boolean escaped = false;

        while (cursor < length) {
            char ch = input.charAt(cursor);

            // Handle escaped characters
            if (escaped) {
                word.append(ch);
                escaped = false;
                cursor++;
                continue;
            }

            // Inside single quotes: everything is literal until closing quote
            if (activeQuote == '\'') {
                if (ch == '\'') {
                    activeQuote = 0;
                } else {
                    word.append(ch);
                }
                cursor++;
                continue;
            }

            // Inside double quotes: handles escapes for ", \, $, and `
            if (activeQuote == '"') {
                if (ch == '\\' && cursor + 1 < length) {
                    char next = input.charAt(cursor + 1);
                    if (next == '"' || next == '\\' || next == '$' || next == '`') {
                        word.append(next);
                        cursor += 2;
                        continue;
                    }
                }
                if (ch == '"') {
                    activeQuote = 0;
                } else {
                    word.append(ch);
                }
                cursor++;
                continue;
            }

            // Outside quotes: check backslash escape
            if (ch == '\\') {
                escaped = true;
                cursor++;
                continue;
            }

            // Quote openers
            if (ch == '\'' || ch == '"') {
                activeQuote = ch;
                cursor++;
                continue;
            }

            // Comments: ignore everything from '#' to end of line
            if (ch == '#' && word.length() == 0) {
                while (cursor < length && input.charAt(cursor) != '\n') {
                    cursor++;
                }
                continue;
            }

            // Whitespace separates words
            if (Character.isWhitespace(ch)) {
                flushWord(word, tokens);
                cursor++;
                continue;
            }

            // Check for multi-character redirection or control operators
            if (ch == '2' && cursor + 1 < length && input.charAt(cursor + 1) == '>') {
                // Check if this '2>' is at start of token
                if (word.length() == 0) {
                    cursor += 2; // skip "2>"
                    if (cursor + 1 < length && input.charAt(cursor) == '&' && input.charAt(cursor + 1) == '1') {
                        cursor += 2;
                        tokens.add(new Token(TokenType.REDIRECT_ERR_TO_OUT, "2>&1"));
                    } else if (cursor < length && input.charAt(cursor) == '>') {
                        cursor++;
                        tokens.add(new Token(TokenType.REDIRECT_ERR_APPEND, "2>>"));
                    } else {
                        tokens.add(new Token(TokenType.REDIRECT_ERR, "2>"));
                    }
                    continue;
                }
            }

            if (ch == '&') {
                flushWord(word, tokens);
                if (cursor + 1 < length && input.charAt(cursor + 1) == '&') {
                    tokens.add(new Token(TokenType.AND_IF, "&&"));
                    cursor += 2;
                } else if (cursor + 1 < length && input.charAt(cursor + 1) == '>') {
                    if (cursor + 2 < length && input.charAt(cursor + 2) == '>') {
                        tokens.add(new Token(TokenType.REDIRECT_ALL_APPEND, "&>>"));
                        cursor += 3;
                    } else {
                        tokens.add(new Token(TokenType.REDIRECT_ALL, "&>"));
                        cursor += 2;
                    }
                } else {
                    tokens.add(new Token(TokenType.AMPERSAND, "&"));
                    cursor++;
                }
                continue;
            }

            if (ch == '|') {
                flushWord(word, tokens);
                if (cursor + 1 < length && input.charAt(cursor + 1) == '|') {
                    tokens.add(new Token(TokenType.OR_IF, "||"));
                    cursor += 2;
                } else {
                    tokens.add(new Token(TokenType.PIPE, "|"));
                    cursor++;
                }
                continue;
            }

            if (ch == ';') {
                flushWord(word, tokens);
                tokens.add(new Token(TokenType.SEMICOLON, ";"));
                cursor++;
                continue;
            }

            if (ch == '>') {
                flushWord(word, tokens);
                if (cursor + 1 < length && input.charAt(cursor + 1) == '>') {
                    tokens.add(new Token(TokenType.REDIRECT_APPEND, ">>"));
                    cursor += 2;
                } else if (cursor + 1 < length && input.charAt(cursor + 1) == '&') {
                    tokens.add(new Token(TokenType.REDIRECT_ALL, ">&"));
                    cursor += 2;
                } else {
                    tokens.add(new Token(TokenType.REDIRECT_OUT, ">"));
                    cursor++;
                }
                continue;
            }

            if (ch == '<') {
                flushWord(word, tokens);
                tokens.add(new Token(TokenType.REDIRECT_IN, "<"));
                cursor++;
                continue;
            }

            // Normal character belonging to a word
            word.append(ch);
            cursor++;
        }

        if (activeQuote != 0) {
            throw new IllegalArgumentException("unclosed quote");
        }

        if (escaped) {
            word.append('\\');
        }

        flushWord(word, tokens);
        tokens.add(new Token(TokenType.EOF, ""));
        return tokens;
    }

    private void flushWord(StringBuilder word, List<Token> tokens) {
        if (word.length() > 0) {
            tokens.add(new Token(TokenType.WORD, word.toString()));
            word.setLength(0);
        }
    }
}

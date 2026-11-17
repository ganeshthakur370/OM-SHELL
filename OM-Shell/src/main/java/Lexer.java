import java.util.ArrayList;

import java.util.List;



public class Lexer {



    private final String input;

    private final int length;

    private int cursor;



    public Lexer(String input) {

        this.input = input != null ? input : "";

        this.length = this.input.length();

        this.cursor = 0;

    }



    

    public List<Token> tokenize() {

        List<Token> tokens = new ArrayList<>();

        StringBuilder word = new StringBuilder();

        char activeQuote = 0;

        boolean escaped = false;



        while (cursor < length) {

            char ch = input.charAt(cursor);



            if (escaped) {

                word.append(ch);

                escaped = false;

                cursor++;

                continue;

            }



            if (activeQuote == '\'') {

                if (ch == '\'') {

                    activeQuote = 0;

                } else {

                    word.append(ch);

                }

                cursor++;

                continue;

            }



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



            if (ch == '\\') {

                escaped = true;

                cursor++;

                continue;

            }



            if (ch == '$' && cursor + 1 < length && input.charAt(cursor + 1) == '\'') {

                cursor += 2;

                String decodedAnsi = parseAnsiCString();

                word.append(decodedAnsi);

                continue;

            }



            if (ch == '$' && cursor + 1 < length && input.charAt(cursor + 1) == '"') {

                cursor += 2;

                activeQuote = '"';

                continue;

            }



            if (ch == '\'' || ch == '"') {

                activeQuote = ch;

                cursor++;

                continue;

            }



            if (ch == '#' && word.length() == 0) {

                while (cursor < length && input.charAt(cursor) != '\n') {

                    cursor++;

                }

                continue;

            }



            if (Character.isWhitespace(ch)) {

                flushWord(word, tokens);

                cursor++;

                continue;

            }



            if (ch == '[' && cursor + 1 < length && input.charAt(cursor + 1) == '[') {

                flushWord(word, tokens);

                tokens.add(new Token(TokenType.DOUBLE_LBRACKET, "[["));

                cursor += 2;

                continue;

            }

            if (ch == ']' && cursor + 1 < length && input.charAt(cursor + 1) == ']') {

                flushWord(word, tokens);

                tokens.add(new Token(TokenType.DOUBLE_RBRACKET, "]]"));

                cursor += 2;

                continue;

            }



            if (ch == '(' && cursor + 1 < length && input.charAt(cursor + 1) == '(') {

                flushWord(word, tokens);

                tokens.add(new Token(TokenType.DOUBLE_LPAREN, "(("));

                cursor += 2;

                continue;

            }

            if (ch == ')' && cursor + 1 < length && input.charAt(cursor + 1) == ')') {

                flushWord(word, tokens);

                tokens.add(new Token(TokenType.DOUBLE_RPAREN, "))"));

                cursor += 2;

                continue;

            }



            if (ch == '(' && word.length() == 0) {

                tokens.add(new Token(TokenType.LPAREN, "("));

                cursor++;

                continue;

            }

            if (ch == ')' && word.length() == 0) {

                tokens.add(new Token(TokenType.RPAREN, ")"));

                cursor++;

                continue;

            }



            if (ch == '{' && word.length() == 0 && (cursor + 1 == length || Character.isWhitespace(input.charAt(cursor + 1)))) {

                tokens.add(new Token(TokenType.LBRACE, "{"));

                cursor++;

                continue;

            }

            if (ch == '}' && word.length() == 0 && (cursor + 1 == length || Character.isWhitespace(input.charAt(cursor + 1)) || input.charAt(cursor + 1) == ';')) {

                tokens.add(new Token(TokenType.RBRACE, "}"));

                cursor++;

                continue;

            }



            if (ch == '2' && cursor + 1 < length && input.charAt(cursor + 1) == '>' && word.length() == 0) {

                cursor += 2;

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

            if (ch == '1' && cursor + 1 < length && input.charAt(cursor + 1) == '>' && word.length() == 0) {

                if (cursor + 3 < length && input.charAt(cursor + 2) == '&' && input.charAt(cursor + 3) == '2') {

                    cursor += 4;

                    tokens.add(new Token(TokenType.REDIRECT_OUT_TO_ERR, "1>&2"));

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

                } else if (cursor + 1 < length && input.charAt(cursor + 1) == '|') {

                    tokens.add(new Token(TokenType.REDIRECT_CLOBBER, ">|"));

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

                if (cursor + 2 < length && input.charAt(cursor + 1) == '<' && input.charAt(cursor + 2) == '<') {

                    tokens.add(new Token(TokenType.REDIRECT_HERE_STRING, "<<<"));

                    cursor += 3;

                } else if (cursor + 1 < length && input.charAt(cursor + 1) == '<') {

                    tokens.add(new Token(TokenType.REDIRECT_HERE_DOC, "<<"));

                    cursor += 2;

                } else {

                    tokens.add(new Token(TokenType.REDIRECT_IN, "<"));

                    cursor++;

                }

                continue;

            }



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



    public static String decodeAnsiC(String str) {

        if (str == null) return "";

        StringBuilder sb = new StringBuilder();

        int len = str.length();

        int i = 0;

        while (i < len) {

            char c = str.charAt(i);

            if (c == '\\' && i + 1 < len) {

                i++;

                char esc = str.charAt(i);

                switch (esc) {

                    case 'a' -> sb.append('\u0007');

                    case 'b' -> sb.append('\b');

                    case 'e', 'E' -> sb.append('\u001B');

                    case 'f' -> sb.append('\f');

                    case 'n' -> sb.append('\n');

                    case 'r' -> sb.append('\r');

                    case 't' -> sb.append('\t');

                    case 'v' -> sb.append('\u000B');

                    case '\\' -> sb.append('\\');

                    case '\'' -> sb.append('\'');

                    case '"' -> sb.append('"');

                    case '?' -> sb.append('?');

                    case 'x' -> {

                        int hexVal = 0;

                        int count = 0;

                        while (i + 1 < len && count < 2) {

                            char h = str.charAt(i + 1);

                            int digit = Character.digit(h, 16);

                            if (digit == -1) break;

                            hexVal = (hexVal * 16) + digit;

                            i++;

                            count++;

                        }

                        sb.append((char) hexVal);

                    }

                    case '0', '1', '2', '3', '4', '5', '6', '7' -> {

                        int octVal = esc - '0';

                        int count = 1;

                        while (i + 1 < len && count < 3) {

                            char o = str.charAt(i + 1);

                            if (o >= '0' && o <= '7') {

                                octVal = (octVal * 8) + (o - '0');

                                i++;

                                count++;

                            } else break;

                        }

                        sb.append((char) octVal);

                    }

                    default -> sb.append(esc);

                }

            } else {

                sb.append(c);

            }

            i++;

        }

        return sb.toString();

    }



    private String parseAnsiCString() {

        StringBuilder sb = new StringBuilder();

        while (cursor < length) {

            char c = input.charAt(cursor);

            if (c == '\'') {

                cursor++;

                return sb.toString();

            }



            if (c == '\\' && cursor + 1 < length) {

                cursor++;

                char esc = input.charAt(cursor);

                switch (esc) {

                    case 'a' -> sb.append('\u0007');

                    case 'b' -> sb.append('\b');

                    case 'e', 'E' -> sb.append('\u001B');

                    case 'f' -> sb.append('\f');

                    case 'n' -> sb.append('\n');

                    case 'r' -> sb.append('\r');

                    case 't' -> sb.append('\t');

                    case 'v' -> sb.append('\u000B');

                    case '\\' -> sb.append('\\');

                    case '\'' -> sb.append('\'');

                    case '"' -> sb.append('"');

                    case '?' -> sb.append('?');

                    case 'x' -> {



                        int hexVal = 0;

                        int count = 0;

                        while (cursor + 1 < length && count < 2) {

                            char h = input.charAt(cursor + 1);

                            int digit = Character.digit(h, 16);

                            if (digit == -1) break;

                            hexVal = (hexVal * 16) + digit;

                            cursor++;

                            count++;

                        }

                        sb.append((char) hexVal);

                    }

                    case '0', '1', '2', '3', '4', '5', '6', '7' -> {



                        int octVal = esc - '0';

                        int count = 1;

                        while (cursor + 1 < length && count < 3) {

                            char o = input.charAt(cursor + 1);

                            if (o >= '0' && o <= '7') {

                                octVal = (octVal * 8) + (o - '0');

                                cursor++;

                                count++;

                            } else break;

                        }

                        sb.append((char) octVal);

                    }

                    default -> sb.append(esc);

                }

                cursor++;

                continue;

            }



            sb.append(c);

            cursor++;

        }



        throw new IllegalArgumentException("unclosed quote in ANSI-C string");

    }



    private void flushWord(StringBuilder word, List<Token> tokens) {

        if (word.length() > 0) {

            tokens.add(new Token(TokenType.WORD, word.toString()));

            word.setLength(0);

        }

    }

}

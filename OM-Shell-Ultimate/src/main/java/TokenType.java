
/**
 * Token types recognized by the OM Shell lexical analyzer.
 */
public enum TokenType {
    WORD,               // Standard argument or command name
    PIPE,               // |
    REDIRECT_IN,        // <
    REDIRECT_OUT,       // >
    REDIRECT_APPEND,    // >>
    REDIRECT_ERR,       // 2>
    REDIRECT_ERR_APPEND,// 2>>
    REDIRECT_ERR_TO_OUT,// 2>&1
    REDIRECT_ALL,       // &>
    REDIRECT_ALL_APPEND,// &>>
    SEMICOLON,          // ;
    AND_IF,             // &&
    OR_IF,              // ||
    AMPERSAND,          // & (background execution)
    EOF                 // End of input stream
}

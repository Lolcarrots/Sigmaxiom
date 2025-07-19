package com.sigmaxiom;

import org.fife.ui.rsyntaxtextarea.*;
import javax.swing.text.Segment;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class FutharkTokenMaker extends AbstractTokenMaker {

    private static final Set<String> KEYWORDS = new HashSet<>(Arrays.asList(
        "if", "then", "else", "let", "loop", "with", "def", "entry", "fn",
        "for", "while", "do", "in", "local", "type", "val", "module", "open", "import"
    ));
    private static final Set<String> TYPES = new HashSet<>(Arrays.asList(
        "i8", "i16", "i32", "i64", "u8", "u16", "u32", "u64", "int", "real", "bool", "char", "f32", "f64"
    ));
    private static final Set<String> BUILTIN_FUNCTIONS = new HashSet<>(Arrays.asList(
        "map", "reduce", "reduce_comm", "scan", "filter", "partition",
        "stream_map", "stream_map_per", "stream_red", "stream_red_per",
        "stream_seq", "iota"
    ));

    
    private static final Set<String> OPERATORS = new HashSet<>(Arrays.asList(
        "+", "-", "*", "/", "%", "!", "=", ">", "<", "&", "|", "^", ":", 
        "==", "!=", "<=", ">=", "&&", "||", "::", ":=", "<-", "->"
    ));
    
    private static final Set<Character> PUNCTUATION = new HashSet<>(Arrays.asList(
        '(', ')', '[', ']', '{', '}', ',', ';', '.'
    ));

    @Override
    public Token getTokenList(Segment text, int initialTokenType, int startOffset) {
        resetTokenList();
        int offset = startOffset;
        int count = text.offset + text.count;
        int i = text.offset;
        int tokenStart = i;
        boolean inString = false;
        boolean inComment = false;

        while (i < count) {
            char c = text.array[i];

            
            if (!inString && !inComment && c == '-' && i + 1 < count && text.array[i + 1] == '-') {
                if (tokenStart < i)
                    addFutharkToken(text, tokenStart, i - 1, offset + (tokenStart - text.offset));
                addToken(text, i, count - 1, Token.COMMENT_EOL, offset + (i - text.offset));
                addNullToken();
                return firstToken;
            }

            
            if (!inComment && c == '"') {
                if (!inString) {
                    if (tokenStart < i)
                        addFutharkToken(text, tokenStart, i - 1, offset + (tokenStart - text.offset));
                    tokenStart = i;
                    inString = true;
                } else {
                    addToken(text, tokenStart, i, Token.LITERAL_STRING_DOUBLE_QUOTE, offset + (tokenStart - text.offset));
                    tokenStart = i + 1;
                    inString = false;
                }
                i++;
                continue;
            }

            
            if (!inString && Character.isWhitespace(c)) {
                if (tokenStart < i)
                    addFutharkToken(text, tokenStart, i - 1, offset + (tokenStart - text.offset));
                addToken(text, i, i, Token.WHITESPACE, offset + (i - text.offset));
                tokenStart = i + 1;
                i++;
                continue;
            }

            
            if (!inString && PUNCTUATION.contains(c)) {
                if (tokenStart < i)
                    addFutharkToken(text, tokenStart, i - 1, offset + (tokenStart - text.offset));
                addToken(text, i, i, Token.SEPARATOR, offset + (i - text.offset));
                tokenStart = i + 1;
                i++;
                continue;
            }

            
            if (!inString) {
                String op2 = null, op1 = null;
                if (i + 1 < count)
                    op2 = "" + c + text.array[i + 1];
                op1 = "" + c;
                if (op2 != null && OPERATORS.contains(op2)) {
                    if (tokenStart < i)
                        addFutharkToken(text, tokenStart, i - 1, offset + (tokenStart - text.offset));
                    addToken(text, i, i + 1, Token.OPERATOR, offset + (i - text.offset));
                    tokenStart = i + 2;
                    i += 2;
                    continue;
                }
                if (OPERATORS.contains(op1)) {
                    if (tokenStart < i)
                        addFutharkToken(text, tokenStart, i - 1, offset + (tokenStart - text.offset));
                    addToken(text, i, i, Token.OPERATOR, offset + (i - text.offset));
                    tokenStart = i + 1;
                    i++;
                    continue;
                }
            }

            i++;
        }

        
        if (tokenStart < count) {
            if (inString)
                addToken(text, tokenStart, count - 1, Token.LITERAL_STRING_DOUBLE_QUOTE, offset + (tokenStart - text.offset));
            else
                addFutharkToken(text, tokenStart, count - 1, offset + (tokenStart - text.offset));
        }

        addNullToken();
        return firstToken;
    }

    private void addFutharkToken(Segment text, int start, int end, int startOffset) {
        if (start > end) return;
        String word = new String(text.array, start, end - start + 1);

        int type;
        if (KEYWORDS.contains(word)) {
            type = Token.RESERVED_WORD;
        } else if (TYPES.contains(word)) {
            type = Token.DATA_TYPE;
        } else if (BUILTIN_FUNCTIONS.contains(word)) {
            type = Token.RESERVED_WORD_2;
        } else if ("true".equals(word) || "false".equals(word)) {
            type = Token.LITERAL_BOOLEAN;
        } else if (isHexNumber(word)) {
            type = Token.LITERAL_NUMBER_HEXADECIMAL;
        } else if (isFloat(word)) {
            type = Token.LITERAL_NUMBER_FLOAT;
        } else if (isInteger(word)) {
            type = Token.LITERAL_NUMBER_DECIMAL_INT;
        } else if (word.matches("[a-zA-Z_][a-zA-Z0-9_']*")) {
            type = Token.IDENTIFIER;
        } else {
            type = Token.IDENTIFIER;
        }
        addToken(text, start, end, type, startOffset);
    }

    private boolean isHexNumber(String s) {
        return s.matches("[+-]?0x[0-9a-fA-F]+([ui](8|16|32|64))?");
    }
    private boolean isFloat(String s) {
        return s.matches("[0-9]+\\.[0-9]+([eE][+-]?[0-9]+)?(f(32|64))?");
    }
    private boolean isInteger(String s) {
        return s.matches("[+-]?[0-9]+([ui](8|16|32|64))?");
    }

    @Override
    public TokenMap getWordsToHighlight() {
        return null;
    }
}


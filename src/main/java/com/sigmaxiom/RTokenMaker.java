package com.sigmaxiom;

import org.fife.ui.rsyntaxtextarea.*;
import javax.swing.text.Segment;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class RTokenMaker extends AbstractTokenMaker {

    
    private static final Set<String> KEYWORDS = new HashSet<>(Arrays.asList(
        "if", "else", "for", "in", "while", "repeat", "function", "return",
        "break", "next", "library", "require", "source"
    ));

    private static final Set<String> LITERALS = new HashSet<>(Arrays.asList(
        "TRUE", "FALSE", "NULL", "NA", "Inf", "NaN"
    ));

    
    private static final Set<String> OPERATORS = new HashSet<>(Arrays.asList(
        "<-", "->", "<<-", "->>", "%%", "%/%", "%in%", "%*%",
        "+", "-", "*", "/", "^", "<", ">", "=", "!", "&", "|", "$", ":",
        "==", "!=", "<=", ">="
    ));

    @Override
    public Token getTokenList(Segment text, int initialTokenType, int startOffset) {
        resetTokenList();
        int offset = startOffset;
        int count = text.offset + text.count;
        int i = text.offset;
        int tokenStart = i;

        while (i < count) {
            char c = text.array[i];

            
            if (c == '#') {
                if (tokenStart < i) {
                    addRToken(text, tokenStart, i - 1, offset + (tokenStart - text.offset));
                }
                addToken(text, i, count - 1, Token.COMMENT_EOL, offset + (i - text.offset));
                addNullToken();
                return firstToken;
            }

            
            if (c == '"' || c == '\'') {
                if (tokenStart < i) {
                    addRToken(text, tokenStart, i - 1, offset + (tokenStart - text.offset));
                }
                int stringEnd = i + 1;
                while (stringEnd < count && text.array[stringEnd] != c) {
                    if (text.array[stringEnd] == '\\' && stringEnd + 1 < count) {
                        stringEnd++; 
                    }
                    stringEnd++;
                }
                int end = Math.min(stringEnd, count - 1);
                addToken(text, i, end, Token.LITERAL_STRING_DOUBLE_QUOTE, offset + (i - text.offset));
                i = end + 1;
                tokenStart = i;
                continue;
            }

            
            if (Character.isWhitespace(c)) {
                if (tokenStart < i) {
                    addRToken(text, tokenStart, i - 1, offset + (tokenStart - text.offset));
                }
                addToken(text, i, i, Token.WHITESPACE, offset + (i - text.offset));
                tokenStart = i + 1;
                i++;
                continue;
            }

            
            String op3 = (i + 2 < count) ? new String(text.array, i, 3) : null;
            String op2 = (i + 1 < count) ? new String(text.array, i, 2) : null;
            String op1 = new String(text.array, i, 1);

            if (op3 != null && OPERATORS.contains(op3)) {
                if (tokenStart < i) addRToken(text, tokenStart, i - 1, offset + (tokenStart - text.offset));
                addToken(text, i, i + 2, Token.OPERATOR, offset + (i - text.offset));
                i += 3;
                tokenStart = i;
                continue;
            }
            if (op2 != null && OPERATORS.contains(op2)) {
                if (tokenStart < i) addRToken(text, tokenStart, i - 1, offset + (tokenStart - text.offset));
                addToken(text, i, i + 1, Token.OPERATOR, offset + (i - text.offset));
                i += 2;
                tokenStart = i;
                continue;
            }
            if (OPERATORS.contains(op1)) {
                if (tokenStart < i) addRToken(text, tokenStart, i - 1, offset + (tokenStart - text.offset));
                addToken(text, i, i, Token.OPERATOR, offset + (i - text.offset));
                i++;
                tokenStart = i;
                continue;
            }

            i++;
        }

        
        if (tokenStart < count) {
            addRToken(text, tokenStart, count - 1, offset + (tokenStart - text.offset));
        }

        addNullToken();
        return firstToken;
    }

    private void addRToken(Segment text, int start, int end, int startOffset) {
        if (start > end) return;
        String word = new String(text.array, start, end - start + 1);

        int type;
        if (KEYWORDS.contains(word)) {
            type = Token.RESERVED_WORD;
        } else if (LITERALS.contains(word)) {
            type = Token.LITERAL_BOOLEAN; 
        } else if (isNumber(word)) {
            type = Token.LITERAL_NUMBER_FLOAT;
        } else {
            type = Token.IDENTIFIER;
        }
        addToken(text, start, end, type, startOffset);
    }

    private boolean isNumber(String s) {
        return s.matches("-?[0-9]+(\\.[0-9]+)?([eE][-+]?[0-9]+)?");
    }

    @Override
    public TokenMap getWordsToHighlight() {
        return null; 
    }
}

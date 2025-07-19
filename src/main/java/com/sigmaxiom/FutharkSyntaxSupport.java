package com.sigmaxiom;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxScheme;
import org.fife.ui.rsyntaxtextarea.Token;
import org.fife.ui.rsyntaxtextarea.Style;

import org.fife.ui.autocomplete.AutoCompletion;
import org.fife.ui.autocomplete.BasicCompletion;
import org.fife.ui.autocomplete.DefaultCompletionProvider;

import java.awt.Color;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;


public final class FutharkSyntaxSupport {

    private final RSyntaxTextArea codeArea;
    private final String selectedLanguage;

    public FutharkSyntaxSupport(RSyntaxTextArea codeArea, String selectedLanguage) {
        this.codeArea = codeArea;
        this.selectedLanguage = selectedLanguage;
    }

    
    public void installHighlighting() {
        SyntaxScheme scheme = (SyntaxScheme) codeArea.getSyntaxScheme().clone();

        
        Color purple   = new Color(0xC586C0); 
        Color teal     = new Color(0x4EC9B0); 
        Color yellow   = new Color(0xDCDCAA); 
        Color blue     = new Color(0x9CDCFE); 
        Color gray     = new Color(0xD4D4D4); 
        Color greenNum = new Color(0xB5CEA8); 
        Color salmon   = new Color(0xCE9178); 
        Color greenCmt = new Color(0x6A9955); 
        Color blueBool = new Color(0x569CD6); 
        

        apply(scheme, Token.RESERVED_WORD,              purple);
        apply(scheme, Token.RESERVED_WORD_2,            blue);
        apply(scheme, Token.DATA_TYPE,                  teal);
        apply(scheme, Token.FUNCTION,                   yellow);
        apply(scheme, Token.IDENTIFIER,                 blue);
        apply(scheme, Token.OPERATOR,                   gray);
        apply(scheme, Token.SEPARATOR,                  gray);
        apply(scheme, Token.LITERAL_STRING_DOUBLE_QUOTE,salmon);
        apply(scheme, Token.LITERAL_NUMBER_DECIMAL_INT, greenNum);
        apply(scheme, Token.LITERAL_NUMBER_FLOAT,       greenNum);
        apply(scheme, Token.LITERAL_NUMBER_HEXADECIMAL, greenNum);
        apply(scheme, Token.LITERAL_BOOLEAN,            blueBool);
        apply(scheme, Token.COMMENT_MULTILINE,          greenCmt);
        apply(scheme, Token.COMMENT_EOL,                greenCmt);

        codeArea.setSyntaxScheme(scheme);

        
        codeArea.setBracketMatchingEnabled(true);
        codeArea.setPaintMatchedBracketPair(true);
        codeArea.setMatchedBracketBGColor(new Color(0x3A3D5C));
        codeArea.setMatchedBracketBorderColor(new Color(0x808080));

        
        codeArea.setAutoIndentEnabled(true);
        codeArea.setCloseCurlyBraces(true);
        codeArea.setPaintTabLines(true);

        setupAutoCompletion();
    }

    
    private static void apply(SyntaxScheme scheme, int tokenType, Color fg) {
        Style s = scheme.getStyle(tokenType);
        if (s == null) {
            s = new Style();
            scheme.setStyle(tokenType, s);
        }
        s.foreground = fg;
        s.background = null;
    }

    

    private void setupAutoCompletion() {
        if (!selectedLanguage.startsWith("Futhark")) {
            return;
        }

        DefaultCompletionProvider provider = new DefaultCompletionProvider();

        KEYWORDS.forEach(k -> provider.addCompletion(new BasicCompletion(provider, k, k + " keyword")));
        TYPES.forEach(t -> provider.addCompletion(new BasicCompletion(provider, t, t + " – primitive type")));
        BUILTIN_FUNCTIONS.forEach(b -> provider.addCompletion(new BasicCompletion(provider, b, b + " – builtin")));

        AutoCompletion ac = new AutoCompletion(provider);
        ac.setShowDescWindow(true);
        ac.setParameterAssistanceEnabled(true);
        ac.setAutoActivationEnabled(true);
        ac.setAutoActivationDelay(300);
        ac.install(codeArea);
    }

    

    public static final Set<String> KEYWORDS = new HashSet<>(Arrays.asList(
        "if","then","else","let","loop","with","def","entry","fn",
        "for","while","do","in",
        "local","type","val","module",
        "open","import"
    ));

    public static final Set<String> TYPES = new HashSet<>(Arrays.asList(
        "i8","i16","i32","i64",
        "u8","u16","u32","u64",
        "int","real","bool","char",
        "f32","f64"
    ));

    public static final Set<String> BUILTIN_FUNCTIONS = new HashSet<>(Arrays.asList(
        "map","reduce","reduce_comm","scan","filter","partition",
        "stream_map","stream_map_per","stream_red","stream_red_per",
        "stream_seq","iota"
    ));
}


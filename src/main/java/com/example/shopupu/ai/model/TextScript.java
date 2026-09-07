package com.example.shopupu.ai.model;

/**
 * Writing system of a piece of text, as far as its letters reveal it.
 *
 * <p>Used to catch the one LLM failure mode that is cheap to detect deterministically:
 * answering in a language nobody asked for. Prompt instructions alone did not hold —
 * review summaries drifted to German, and once that was forbidden, to Ukrainian, for
 * a catalogue whose reviews are entirely English.
 */
public enum TextScript {
    LATIN,
    CYRILLIC,
    /** No letters, or both scripts present — not enough signal to overrule the model. */
    UNDETERMINED;

    public static TextScript of(String text) {
        if (text == null) {
            return UNDETERMINED;
        }
        int latin = 0;
        int cyrillic = 0;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (!Character.isLetter(ch)) {
                continue;
            }
            Character.UnicodeBlock block = Character.UnicodeBlock.of(ch);
            if (block == Character.UnicodeBlock.CYRILLIC
                    || block == Character.UnicodeBlock.CYRILLIC_SUPPLEMENTARY) {
                cyrillic++;
            } else if (block == Character.UnicodeBlock.BASIC_LATIN
                    || block == Character.UnicodeBlock.LATIN_1_SUPPLEMENT
                    || block == Character.UnicodeBlock.LATIN_EXTENDED_A) {
                latin++;
            }
        }
        if (latin > 0 && cyrillic == 0) {
            return LATIN;
        }
        if (cyrillic > 0 && latin == 0) {
            return CYRILLIC;
        }
        return UNDETERMINED;
    }
}

package com.minaret.client;

import com.minaret.MinaretMod;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Prefix trie for chord key sequences. Each path from root to a node with
 * a non-null {@code sequence} represents a complete chord.
 */
public final class ChordTrie {

    public static final class Node {
        final Map<Integer, Node> children = new HashMap<>();
        String sequence; // non-null at leaf/complete nodes
    }

    private ChordTrie() {}

    /** Build a trie from chord sequence strings like "f>1", "f>f>1". */
    public static Node build(Set<String> sequences) {
        Node root = new Node();
        for (String sequence : sequences) {
            String[] parts = sequence.split(">");
            Node node = root;
            boolean valid = true;
            for (String part : parts) {
                int keyCode = KeyNames.toKeyCode(part.trim());
                if (keyCode < 0) {
                    MinaretMod.LOGGER.warn(
                        "Unknown key '{}' in chord '{}'", part.trim(), sequence
                    );
                    valid = false;
                    break;
                }
                node = node.children.computeIfAbsent(keyCode, k -> new Node());
            }
            if (valid) node.sequence = sequence;
        }
        return root;
    }
}

package dev.boltmc.friends;

/** One line of chat with a friend, kept only in memory (not written to disk). */
public record ChatMessage(String from, String text, long atMs, boolean mine) {}

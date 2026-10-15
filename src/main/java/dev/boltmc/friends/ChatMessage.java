package dev.boltmc.friends;

/** One line of chat with a friend, kept only in memory (not written to disk). */
public record ChatMessage(String fromUuid, String text, long atMs, boolean mine) {}

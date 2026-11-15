package com.margin;

/** One message of earlier conversation. role is "user" or "model". */
public record Turn(String role, String text) {}

package ch.swissqcommerce.backend.domain.feedback.core.model;

import lombok.Value;

@Value
public class Comment {
    String text;

    public Comment(String text) {
        if (text != null && text.length() > 1000) {
            throw new IllegalArgumentException("Comment exceeds maximum length of 1000 characters");
        }
        this.text = text;
    }
}

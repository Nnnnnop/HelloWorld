package com.example.polyusigwebsite.exception;

public class ResourceFileNotFoundException extends RuntimeException {
    public ResourceFileNotFoundException(Long id) {
        super("File not found, id=" + id);
    }
}

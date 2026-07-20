package com.interviewdeck.mcp.model;

import java.util.List;
import java.util.Map;

public record UserData(
        List<String> bookmarks,
        List<String> progress,
        Map<String, String> notes
) {}

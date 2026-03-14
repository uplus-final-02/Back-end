package org.backend.userapi.search.service;

import java.util.List;

public interface SuggestionService {
    List<String> getSuggestions(String keyword);
    String getDidYouMean(String keyword);
}
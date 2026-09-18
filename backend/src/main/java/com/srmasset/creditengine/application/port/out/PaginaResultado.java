package com.srmasset.creditengine.application.port.out;

import java.util.List;

public record PaginaResultado<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
}

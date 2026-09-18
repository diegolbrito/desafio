package com.srmasset.creditengine.adapter.in.web.dto;

import com.srmasset.creditengine.application.port.out.PaginaResultado;

import java.util.List;
import java.util.function.Function;

/** Envelope de paginacao conforme convencao REST do SPEC: { content, page, size, totalElements, totalPages }. */
public record PaginaResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <S, T> PaginaResponse<T> from(PaginaResultado<S> pagina, Function<S, T> mapper) {
        List<T> content = pagina.content().stream().map(mapper).toList();
        return new PaginaResponse<>(content, pagina.page(), pagina.size(), pagina.totalElements(), pagina.totalPages());
    }
}

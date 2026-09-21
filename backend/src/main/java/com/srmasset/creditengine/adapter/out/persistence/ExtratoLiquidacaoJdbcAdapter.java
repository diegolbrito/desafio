package com.srmasset.creditengine.adapter.out.persistence;

import com.srmasset.creditengine.application.port.out.BuscarExtratoLiquidacaoPort;
import com.srmasset.creditengine.application.port.out.ExtratoLiquidacaoItem;
import com.srmasset.creditengine.application.port.out.FiltroExtratoLiquidacao;
import com.srmasset.creditengine.application.port.out.PaginaResultado;
import com.srmasset.creditengine.domain.CategoriaRisco;
import com.srmasset.creditengine.domain.Moeda;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * SQL nativo (nao JPA/Criteria) de proposito - ver SPEC.md item 12: relatorio
 * analitico com filtros dinamicos e paginacao, onde controlar o SQL/plano de
 * execucao diretamente compensa mais que a conveniencia do ORM. E' o unico
 * adapter de leitura do sistema que nao passa pelo Hibernate - por isso o
 * "deleted_at is null" abaixo e' manual: {@code @SQLRestriction} (ver
 * RecebivelEntity) so' se aplica a consultas que passam pelo Hibernate, nao a
 * JDBC puro.
 *
 * <p>Contagem total de paginas via {@code count(*) OVER()} na mesma consulta,
 * evitando um segundo round-trip so' para o total.
 */
@Component
public class ExtratoLiquidacaoJdbcAdapter implements BuscarExtratoLiquidacaoPort {

    private static final String SQL = """
            SELECT r.id AS recebivel_id, r.lote_recebivel_id AS lote_id, r.ativo, r.valor_bruto,
                   r.valor_presente, r.valor_desagio, r.moeda_pagamento, r.cotacao_cambio,
                   r.categoria_risco, r.data_vencimento, r.liquidado_em,
                   count(*) OVER() AS total_count
            FROM recebivel r
            WHERE r.status = 'LIQUIDADO'
              AND r.deleted_at IS NULL
              AND (:dataInicio::timestamptz IS NULL OR r.liquidado_em >= :dataInicio)
              AND (:dataFim::timestamptz IS NULL OR r.liquidado_em <= :dataFim)
              AND (:ativo::text IS NULL OR r.ativo ILIKE '%' || :ativo || '%')
              AND (:moeda::text IS NULL OR r.moeda_pagamento = :moeda)
            ORDER BY r.liquidado_em DESC
            LIMIT :size OFFSET :offset
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ExtratoLiquidacaoJdbcAdapter(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public PaginaResultado<ExtratoLiquidacaoItem> buscar(FiltroExtratoLiquidacao filtro, int page, int size) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("dataInicio", toTimestamp(filtro.dataInicio()))
                .addValue("dataFim", toTimestamp(filtro.dataFim()))
                .addValue("ativo", filtro.ativo())
                .addValue("moeda", filtro.moeda() == null ? null : filtro.moeda().name())
                .addValue("size", size)
                .addValue("offset", page * size);

        long[] totalHolder = {0L};
        List<ExtratoLiquidacaoItem> content = jdbcTemplate.query(SQL, params, (rs, rowNum) -> {
            totalHolder[0] = rs.getLong("total_count");
            return new ExtratoLiquidacaoItem(
                    (UUID) rs.getObject("recebivel_id"),
                    (UUID) rs.getObject("lote_id"),
                    rs.getString("ativo"),
                    rs.getBigDecimal("valor_bruto"),
                    rs.getBigDecimal("valor_presente"),
                    rs.getBigDecimal("valor_desagio"),
                    Moeda.valueOf(rs.getString("moeda_pagamento")),
                    rs.getBigDecimal("cotacao_cambio"),
                    CategoriaRisco.valueOf(rs.getString("categoria_risco")),
                    rs.getObject("data_vencimento", LocalDate.class),
                    toOffsetDateTime(rs.getTimestamp("liquidado_em")));
        });

        long totalElements = totalHolder[0];
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        return new PaginaResultado<>(content, page, size, totalElements, totalPages);
    }

    private static Timestamp toTimestamp(OffsetDateTime dateTime) {
        return dateTime == null ? null : Timestamp.from(dateTime.toInstant());
    }

    private static OffsetDateTime toOffsetDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant().atOffset(ZoneOffset.UTC);
    }
}

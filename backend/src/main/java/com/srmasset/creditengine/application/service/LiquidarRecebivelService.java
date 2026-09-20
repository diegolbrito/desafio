package com.srmasset.creditengine.application.service;

import com.srmasset.creditengine.application.port.in.LiquidarRecebivelUseCase;
import com.srmasset.creditengine.application.port.out.LiquidarRecebivelPort;
import com.srmasset.creditengine.application.port.out.RegistrarEventoTransacaoPort;
import com.srmasset.creditengine.domain.EventoTransacao;
import com.srmasset.creditengine.domain.Recebivel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Orquestra o caso de uso "liquidar recebivel" (ver SPEC.md "Premissas
 * adotadas" - liquidacao). Idempotente: uma segunda chamada para um recebivel
 * ja LIQUIDADO nao gera novo evento de auditoria nem altera o estado - apenas
 * retorna o mesmo resultado (a garantia de nao duplicar vem de
 * {@link Recebivel#liquidar}, combinada ao bloqueio pessimista aplicado por
 * {@link LiquidarRecebivelPort#buscarParaLiquidar}).
 */
public class LiquidarRecebivelService implements LiquidarRecebivelUseCase {

    private static final Logger log = LoggerFactory.getLogger(LiquidarRecebivelService.class);

    private final LiquidarRecebivelPort liquidarRecebivelPort;
    private final RegistrarEventoTransacaoPort registrarEventoPort;
    private final Clock clock;

    public LiquidarRecebivelService(LiquidarRecebivelPort liquidarRecebivelPort,
                                     RegistrarEventoTransacaoPort registrarEventoPort,
                                     Clock clock) {
        this.liquidarRecebivelPort = liquidarRecebivelPort;
        this.registrarEventoPort = registrarEventoPort;
        this.clock = clock;
    }

    @Override
    public Optional<Recebivel> liquidar(UUID loteId, UUID recebivelId) {
        Optional<Recebivel> recebivelOpt = liquidarRecebivelPort.buscarParaLiquidar(loteId, recebivelId);
        if (recebivelOpt.isEmpty()) {
            return Optional.empty();
        }

        Recebivel recebivel = recebivelOpt.get();
        OffsetDateTime agora = OffsetDateTime.now(clock);
        boolean liquidadoAgora = recebivel.liquidar(agora);
        liquidarRecebivelPort.salvar(recebivel);

        if (liquidadoAgora) {
            registrarEventoPort.registrar(EventoTransacao.recebivelLiquidado(loteId, recebivel, agora));
            log.info("Recebivel {} liquidado", recebivelId);
        } else {
            log.info("Recebivel {} ja estava liquidado - requisicao idempotente, sem novo efeito", recebivelId);
        }

        return Optional.of(recebivel);
    }
}

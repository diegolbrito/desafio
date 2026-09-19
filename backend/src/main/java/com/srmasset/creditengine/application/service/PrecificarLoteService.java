package com.srmasset.creditengine.application.service;

import com.srmasset.creditengine.application.exception.ReferenciaNaoEncontradaException;
import com.srmasset.creditengine.application.port.in.ComandoPrecificarLote;
import com.srmasset.creditengine.application.port.in.PrecificarLoteUseCase;
import com.srmasset.creditengine.application.port.out.CategoriaRiscoRepositoryPort;
import com.srmasset.creditengine.application.port.out.RegistrarEventoTransacaoPort;
import com.srmasset.creditengine.application.port.out.SalvarLoteRecebiveisPort;
import com.srmasset.creditengine.application.port.out.TaxaBaseRepositoryPort;
import com.srmasset.creditengine.domain.CalculadoraDesagio;
import com.srmasset.creditengine.domain.ConversorCambial;
import com.srmasset.creditengine.domain.EventoTransacao;
import com.srmasset.creditengine.domain.LoteRecebiveis;
import com.srmasset.creditengine.domain.Recebivel;
import com.srmasset.creditengine.domain.exception.PrazoInvalidoException;
import com.srmasset.creditengine.domain.ResultadoDesagio;
import com.srmasset.creditengine.domain.StatusLote;
import com.srmasset.creditengine.domain.StatusRecebivel;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Orquestra o caso de uso "precificar lote": monta o agregado de dominio, aplica a
 * formula de desagio a cada recebivel (rejeitando individualmente os que tiverem
 * prazo invalido, sem abortar o lote - ver SPEC.md "Premissas adotadas" item 5),
 * persiste o resultado e registra os eventos de auditoria.
 */
public class PrecificarLoteService implements PrecificarLoteUseCase {

    private final CalculadoraDesagio calculadora = new CalculadoraDesagio();
    private final ConversorCambial conversorCambial = new ConversorCambial();

    private final TaxaBaseRepositoryPort taxaBaseRepository;
    private final CategoriaRiscoRepositoryPort categoriaRiscoRepository;
    private final SalvarLoteRecebiveisPort salvarLotePort;
    private final RegistrarEventoTransacaoPort registrarEventoPort;
    private final BigDecimal custoOperacionalPadrao;
    private final Clock clock;

    public PrecificarLoteService(TaxaBaseRepositoryPort taxaBaseRepository,
                                  CategoriaRiscoRepositoryPort categoriaRiscoRepository,
                                  SalvarLoteRecebiveisPort salvarLotePort,
                                  RegistrarEventoTransacaoPort registrarEventoPort,
                                  BigDecimal custoOperacionalPadrao,
                                  Clock clock) {
        this.taxaBaseRepository = taxaBaseRepository;
        this.categoriaRiscoRepository = categoriaRiscoRepository;
        this.salvarLotePort = salvarLotePort;
        this.registrarEventoPort = registrarEventoPort;
        this.custoOperacionalPadrao = custoOperacionalPadrao;
        this.clock = clock;
    }

    @Override
    public LoteRecebiveis precificar(ComandoPrecificarLote comando) {
        LocalDate dataReferencia = LocalDate.now(clock);

        List<Recebivel> recebiveis = comando.recebiveis().stream()
                .map(r -> Recebivel.criar(r.cedente(), r.valorBruto(), r.moeda(), r.dataVencimento(), r.categoriaRisco(),
                        r.moedaPagamento(), r.cotacaoCambio()))
                .toList();

        LoteRecebiveis lote = LoteRecebiveis.criar(dataReferencia, recebiveis);

        String motivoErro = null;
        try {
            for (Recebivel recebivel : lote.getRecebiveis()) {
                precificarItem(recebivel, dataReferencia);
            }
            lote.marcarPrecificado();
        } catch (ReferenciaNaoEncontradaException e) {
            lote.marcarErro();
            motivoErro = e.getMessage();
        }

        LoteRecebiveis loteSalvo = salvarLotePort.salvar(lote);
        registrarEventos(loteSalvo, motivoErro);
        return loteSalvo;
    }

    private void precificarItem(Recebivel recebivel, LocalDate dataReferencia) {
        try {
            long prazoMeses = recebivel.calcularPrazoMeses(dataReferencia);
            BigDecimal taxaBase = taxaBaseRepository.buscarTaxaVigente(recebivel.getMoeda());
            BigDecimal spreadRisco = categoriaRiscoRepository.buscarSpread(recebivel.getCategoriaRisco());
            ResultadoDesagio resultado = calculadora.calcular(
                    recebivel.getValorBruto(), prazoMeses,
                    taxaBase, spreadRisco, custoOperacionalPadrao);
            resultado = conversorCambial.converter(resultado, recebivel.getValorBruto(),
                    recebivel.getMoeda(), recebivel.getMoedaPagamento(), recebivel.getCotacaoCambio());
            recebivel.aplicarPrecificacao(resultado);
        } catch (PrazoInvalidoException e) {
            recebivel.rejeitar(e.getMessage());
        }
    }

    private void registrarEventos(LoteRecebiveis lote, String motivoErro) {
        OffsetDateTime agora = OffsetDateTime.now(clock);
        registrarEventoPort.registrar(EventoTransacao.loteRecebido(lote.getId(), agora));

        for (Recebivel recebivel : lote.getRecebiveis()) {
            if (recebivel.getStatus() == StatusRecebivel.PRECIFICADO) {
                registrarEventoPort.registrar(EventoTransacao.recebivelPrecificado(lote.getId(), recebivel, agora));
            } else if (recebivel.getStatus() == StatusRecebivel.REJEITADO) {
                registrarEventoPort.registrar(EventoTransacao.recebivelRejeitado(lote.getId(), recebivel, agora));
            }
            // PENDENTE: item nao processado por erro sistemico no lote, sem evento proprio.
        }

        EventoTransacao eventoFinal = lote.getStatus() == StatusLote.PRECIFICADO
                ? EventoTransacao.lotePrecificado(lote.getId(), agora)
                : EventoTransacao.loteComErro(lote.getId(), motivoErro, agora);
        registrarEventoPort.registrar(eventoFinal);
    }
}

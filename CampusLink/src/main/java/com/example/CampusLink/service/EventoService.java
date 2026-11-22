// regras de eventos coordenam agenda turmas e materiais associados
package com.example.CampusLink.service;

import com.example.CampusLink.repository.EventoConteudoRepository;
import com.example.CampusLink.repository.UsuarioRepository;
import com.example.CampusLink.model.Evento;
import com.example.CampusLink.repository.EventoRepository;

import lombok.RequiredArgsConstructor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.MessageFormatter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.io.PrintWriter;
import java.io.StringWriter;

// servico da agenda esta classe grava altera consulta e exclui eventos com seus vinculos
@Service
@RequiredArgsConstructor
public class EventoService {

    @Autowired
    private UsuarioRepository UsuarioRepository;

    private static final Logger logger = LoggerFactory.getLogger(EventoService.class);

    private final EventoRepository eventoRepository;
    private final EventoConteudoRepository eventoConteudoRepository;

    // validar texto do evento exige titulo preenchido e limita os textos recebidos
    public static String validarTextoEvento(String nome, String descricao, int limiteNome) {
        // exigir titulo do evento recusa um nome ausente ou em branco
        if (nome == null || nome.isBlank()) {
            return "Informe o nome do evento.";
        }

        // limitar titulo do evento recusa nomes acima do tamanho permitido pela tela
        if (nome.length() > limiteNome) {
            return "O nome do evento deve ter no máximo " + limiteNome + " caracteres.";
        }

        // limitar descricao do evento recusa textos acima do limite usado pelos formularios
        if (descricao != null && descricao.length() > 255) {
            return "A descrição do evento deve ter no máximo 255 caracteres.";
        }

        return null;
    }

    // validar horario do evento este metodo exige termino posterior ao inicio e no mesmo dia
    public static String validarHorario(LocalDateTime inicio, LocalDateTime fim) {
        // validar horario do evento exige que o fim seja depois do inicio
        if (!fim.isAfter(inicio)) {
            return "O horário de término deve ser depois do início.";
        }
        // validar dia do evento exige inicio e fim no mesmo dia
        if (!fim.toLocalDate().equals(inicio.toLocalDate())) {
            return "O evento deve começar e terminar no mesmo dia.";
        }
        return null;
    }


    // cadastrar evento com materiais este metodo salva o evento associa os conteudos e carrega seus nomes
    public Evento adicionarEvento(Evento evento, List<Long> idsConteudos) {

        // cadastrar evento tenta salvar o evento associar os conteudos e preencher seus detalhes
        try {

            Evento eventoSalvo = eventoRepository.save(evento);
            logger.info("Evento salvo no banco id={} professorId={} turmaId={}", eventoSalvo.getId(), eventoSalvo.getIdProfessor(), eventoSalvo.getIdTurma());

            // registro de informacoes confere se esta habilitado ao cadastrar evento com materiais
            if (logger.isInfoEnabled()) {
                // gravar historico ao cadastrar evento com materiais tenta salvar o registro no banco e informa falhas
                try {
                    UsuarioRepository.InserirLogsNoBD(null, "INFO", EventoService.class.getName(), "adicionarEvento", null,
                            MessageFormatter.arrayFormat("Evento salvo no banco id={} professorId={} turmaId={}", new Object[]{eventoSalvo.getId(), eventoSalvo.getIdProfessor(), eventoSalvo.getIdTurma()}).getMessage(), null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            // associar conteudos ao evento salva os vinculos quando foram informados materiais
            if (idsConteudos != null && !idsConteudos.isEmpty()) {

                eventoConteudoRepository.associarConteudos(eventoSalvo.getId(), idsConteudos);
                logger.info("Conteudos associados eventoId={} conteudos={}", eventoSalvo.getId(), idsConteudos);

                // registro de informacoes confere se esta habilitado ao cadastrar evento com materiais
                if (logger.isInfoEnabled()) {
                    // gravar historico ao cadastrar evento com materiais tenta salvar o registro no banco e informa falhas
                    try {
                        UsuarioRepository.InserirLogsNoBD(null, "INFO", EventoService.class.getName(), "adicionarEvento", null,
                                MessageFormatter.arrayFormat("Conteudos associados eventoId={} conteudos={}", new Object[]{eventoSalvo.getId(), idsConteudos}).getMessage(), null, null);
                    } catch (Exception erroLogBD) {
                        logger.error("Erro ao gravar log no banco.", erroLogBD);
                    }
                }
            }

            preencherDetalhesDoEvento(eventoSalvo);
            return eventoSalvo;

        } catch (DataAccessException | SQLException e) {

            logger.error("Erro ao salvar evento professorId={} turmaId={}", evento.getIdProfessor(), evento.getIdTurma(), e);

            // registro de erros confere se esta habilitado ao cadastrar evento com materiais
            if (logger.isErrorEnabled()) {
                // gravar historico ao cadastrar evento com materiais tenta salvar o registro no banco e informa falhas
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));

                    UsuarioRepository.InserirLogsNoBD(null, "ERROR", EventoService.class.getName(), "adicionarEvento", null,
                            MessageFormatter.arrayFormat("Erro ao salvar evento professorId={} turmaId={}", new Object[]{evento.getIdProfessor(), evento.getIdTurma()}).getMessage(), null, excecaoLogBD.toString());
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            throw new RuntimeException("Erro ao salvar evento ou associar conteudos", e);
        }
    }


    // listar eventos com detalhes este metodo consulta todos os eventos e completa turma e materiais
    public List<Evento> listarEventos() {

        // listar eventos consulta todos os eventos e preenche seus detalhes
        try {

            List<Evento> eventos = eventoRepository.findAll();

            preencherDetalhesDosEventos(eventos);

            logger.debug("Eventos encontrados quantidade={}", eventos.size());

            // registro de detalhes confere se esta habilitado ao listar eventos com detalhes
            if (logger.isDebugEnabled()) {
                // gravar historico ao listar eventos com detalhes tenta salvar o registro no banco e informa falhas
                try {
                    UsuarioRepository.InserirLogsNoBD(null, "DEBUG", EventoService.class.getName(), "listarEventos", null,
                            MessageFormatter.arrayFormat("Eventos encontrados quantidade={}", new Object[]{eventos.size()}).getMessage(), null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            return eventos;

        } catch (DataAccessException e) {

            logger.error("Erro ao buscar eventos", e);

            // registro de erros confere se esta habilitado ao listar eventos com detalhes
            if (logger.isErrorEnabled()) {
                // gravar historico ao listar eventos com detalhes tenta salvar o registro no banco e informa falhas
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));

                    UsuarioRepository.InserirLogsNoBD(null, "ERROR", EventoService.class.getName(), "listarEventos", null,
                            "Erro ao buscar eventos", null, excecaoLogBD.toString());
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            throw e;
        }
    }


    // listar eventos das turmas este metodo consulta so as turmas informadas e completa turma e materiais
    public List<Evento> listarEventosDasTurmas(Collection<Long> idsTurmas) {
        // ignorar consulta sem turmas devolve lista vazia quando a pessoa nao participa de turmas
        if (idsTurmas == null || idsTurmas.isEmpty()) {
            return new ArrayList<>();
        }
        List<Evento> eventos = eventoRepository.buscarDasTurmas(idsTurmas);
        preencherDetalhesDosEventos(eventos);
        return eventos;
    }

    // contar eventos este metodo informa o total cadastrado sem carregar os eventos
    public long contarEventos() {
        return eventoRepository.count();
    }

    // listar eventos para o administrador este metodo filtra e pagina no banco e completa so os eventos da pagina
    public Page<Evento> listarEventosParaAdmin(String busca, String tipo, String status, int pagina, int tamanho) {
        String padrao = busca == null || busca.isBlank() ? ""
                : "%" + busca.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        Page<Evento> eventos = eventoRepository.buscarParaAdmin(padrao, tipo, status, PageRequest.of(Math.max(0, pagina), tamanho));
        // voltar para a ultima pagina quando o numero pedido passa do total de resultados
        if (eventos.getContent().isEmpty() && eventos.getTotalPages() > 0 && pagina >= eventos.getTotalPages()) {
            eventos = eventoRepository.buscarParaAdmin(padrao, tipo, status, PageRequest.of(eventos.getTotalPages() - 1, tamanho));
        }
        preencherDetalhesDosEventos(eventos.getContent());
        return eventos;
    }

    // listar eventos das turmas no periodo este metodo consulta so os eventos visiveis na janela e completa turma e materiais
    public List<Evento> listarEventosDasTurmasNoPeriodo(Collection<Long> idsTurmas, LocalDateTime inicio, LocalDateTime fim) {

        // ignorar consulta sem turmas devolve lista vazia quando a pessoa nao participa de turmas
        if (idsTurmas == null || idsTurmas.isEmpty()) {
            return new ArrayList<>();
        }

        // listar eventos do periodo consulta a janela e preenche os detalhes de todos os eventos de uma vez
        try {
            List<Evento> eventos = eventoRepository.buscarDasTurmasNoPeriodo(idsTurmas, inicio, fim);
            preencherDetalhesDosEventos(eventos);
            return eventos;

        } catch (DataAccessException e) {
            logger.error("Erro ao buscar eventos do periodo inicio={} fim={}", inicio, fim, e);
            throw e;
        }
    }


    // buscar evento com detalhes este metodo consulta o identificador e completa os nomes associados
    public Evento buscarEventoPorId(Long idEvento) {

        // buscar evento ignora uma busca sem codigo
        if (idEvento == null) {
            return null;
        }

        Evento evento = eventoRepository.findById(idEvento).orElse(null);

        // preencher evento encontrado acrescenta os detalhes quando o evento existe
        if (evento != null) {
            preencherDetalhesDoEvento(evento);
        }

        return evento;
    }


    // editar evento com materiais este metodo valida os dados atualiza o evento e substitui os vinculos de conteudos
    @Transactional
    public Evento atualizarEvento(Long idEvento, Evento dadosAtualizados, List<Long> idsConteudos) {

        // editar evento exige o codigo e os dados da alteracao
        if (idEvento == null || dadosAtualizados == null) {
            throw new IllegalArgumentException("Informe os dados do evento");
        }

        // validar dados do evento exige nome horarios turma e professor
        if (dadosAtualizados.getNome() == null || dadosAtualizados.getNome().isBlank()
                || dadosAtualizados.getInicio() == null || dadosAtualizados.getFim() == null
                || dadosAtualizados.getIdTurma() == null || dadosAtualizados.getIdProfessor() == null) {
            throw new IllegalArgumentException("Preencha os campos obrigatorios do evento");
        }

        // validar periodo do evento rejeita o fim anterior ao inicio
        if (dadosAtualizados.getFim().isBefore(dadosAtualizados.getInicio())) {
            throw new IllegalArgumentException("A data final nao pode ser anterior ao inicio");
        }

        // atualizar evento tenta salvar os dados substituir os conteudos e carregar os detalhes
        try {

            Evento evento = eventoRepository.findById(idEvento)
                    .orElseThrow(() -> new IllegalArgumentException("Evento nao encontrado"));


            evento.setNome(dadosAtualizados.getNome().trim());
            evento.setTipo(dadosAtualizados.getTipo());
            evento.setDescricao(dadosAtualizados.getDescricao());
            evento.setInicio(dadosAtualizados.getInicio());
            evento.setFim(dadosAtualizados.getFim());
            evento.setPrioridade(dadosAtualizados.getPrioridade());
            evento.setIdTurma(dadosAtualizados.getIdTurma());
            evento.setIdProfessor(dadosAtualizados.getIdProfessor());

            Evento eventoSalvo = eventoRepository.saveAndFlush(evento);


            eventoConteudoRepository.substituirConteudosDoEvento(idEvento, idsConteudos);

            preencherDetalhesDoEvento(eventoSalvo);

            logger.info("Evento atualizado id={} professorId={} turmaId={}",
                    idEvento, eventoSalvo.getIdProfessor(), eventoSalvo.getIdTurma());

            return eventoSalvo;

        } catch (RuntimeException e) {

            logger.error("Erro ao atualizar evento id={}", idEvento, e);
            throw e;
        }
    }


    // delete evento completo este metodo confere a existencia e apaga os vinculos antes de remover o evento
    public void excluirEvento(Long idEvento) {

        // delete evento exige o codigo do evento
        if (idEvento == null) {
            throw new IllegalArgumentException("O ID do evento nao pode ser nulo");
        }

        // delete evento tenta apagar os vinculos e o evento e registra falhas
        try {

            // conferir evento para delete rejeita a exclusao quando o evento nao existe
            if (!eventoRepository.existsById(idEvento)) {

                logger.warn("Evento nao encontrado eventoId={}", idEvento);

                // registro de avisos confere se esta habilitado ao delete evento completo
                if (logger.isWarnEnabled()) {
                    // gravar historico ao delete evento completo tenta salvar o registro no banco e informa falhas
                    try {
                        UsuarioRepository.InserirLogsNoBD(null, "WARN", EventoService.class.getName(), "excluirEvento", null,
                                MessageFormatter.arrayFormat("Evento nao encontrado eventoId={}", new Object[]{idEvento}).getMessage(), null, null);
                    } catch (Exception erroLogBD) {
                        logger.error("Erro ao gravar log no banco.", erroLogBD);
                    }
                }

                throw new IllegalArgumentException("Evento nao encontrado");
            }

            eventoConteudoRepository.excluirAssociacoesDoEvento(idEvento);
            eventoRepository.deleteById(idEvento);

            logger.info("Evento excluido eventoId={}", idEvento);

            // registro de informacoes confere se esta habilitado ao delete evento completo
            if (logger.isInfoEnabled()) {
                // gravar historico ao delete evento completo tenta salvar o registro no banco e informa falhas
                try {
                    UsuarioRepository.InserirLogsNoBD(null, "INFO", EventoService.class.getName(), "excluirEvento", null,
                            MessageFormatter.arrayFormat("Evento excluido eventoId={}", new Object[]{idEvento}).getMessage(), null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

        } catch (DataAccessException | SQLException e) {

            logger.error("Erro ao excluir evento eventoId={}", idEvento, e);

            // registro de erros confere se esta habilitado ao delete evento completo
            if (logger.isErrorEnabled()) {
                // gravar historico ao delete evento completo tenta salvar o registro no banco e informa falhas
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));

                    UsuarioRepository.InserirLogsNoBD(null, "ERROR", EventoService.class.getName(), "excluirEvento", null,
                            MessageFormatter.arrayFormat("Erro ao excluir evento eventoId={}", new Object[]{idEvento}).getMessage(), null, excecaoLogBD.toString());
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            throw new RuntimeException("Erro ao excluir evento", e);
        }
    }


    // completar detalhes da lista este metodo busca turmas e materiais de todos os eventos em duas consultas em vez de duas por evento
    private void preencherDetalhesDosEventos(List<Evento> eventos) {

        // completar lista vazia nao consulta o banco
        if (eventos == null || eventos.isEmpty()) {
            return;
        }

        Set<Long> idsTurmas = new HashSet<>();
        Set<Long> idsEventos = new HashSet<>();
        for (Evento evento : eventos) {
            // separar codigos guarda apenas turmas e eventos informados
            if (evento.getIdTurma() != null) {
                idsTurmas.add(evento.getIdTurma());
            }
            if (evento.getId() != null) {
                idsEventos.add(evento.getId());
            }
        }

        Map<Long, String> nomesTurmas = new HashMap<>();
        // buscar nomes das turmas consulta todas as turmas da lista de uma vez
        if (!idsTurmas.isEmpty()) {
            for (Object[] linha : eventoRepository.buscarNomesTurmas(idsTurmas)) {
                nomesTurmas.put(((Number) linha[0]).longValue(), (String) linha[1]);
            }
        }

        Map<Long, List<String>> conteudosPorEvento = new HashMap<>();
        // buscar materiais dos eventos consulta os titulos de todos os eventos de uma vez mantendo a ordem alfabetica
        if (!idsEventos.isEmpty()) {
            for (Object[] linha : eventoRepository.buscarConteudosDosEventos(idsEventos)) {
                conteudosPorEvento.computeIfAbsent(((Number) linha[0]).longValue(), id -> new ArrayList<>()).add((String) linha[1]);
            }
        }

        for (Evento evento : eventos) {
            evento.setNomeTurma(evento.getIdTurma() != null ? nomesTurmas.get(evento.getIdTurma()) : null);
            evento.setNomesConteudos(evento.getId() != null ? conteudosPorEvento.getOrDefault(evento.getId(), List.of()) : List.of());
        }
    }


    // completar detalhes do evento este metodo consulta o nome da turma e os titulos dos materiais
    private void preencherDetalhesDoEvento(Evento evento) {

        // preencher detalhes do evento ignora um evento ausente
        if (evento == null) {
            return;
        }


        String nomeTurma = null;
        // buscar nome da turma consulta o nome quando o evento informa uma turma
        if (evento.getIdTurma() != null) {
            nomeTurma = eventoRepository.buscarNomeTurma(evento.getIdTurma()).orElse(null);
        }
        evento.setNomeTurma(nomeTurma);

        List<String> nomesConteudos = List.of();
        // buscar conteudos do evento consulta os nomes quando o evento tem um codigo
        if (evento.getId() != null) {
            nomesConteudos = eventoRepository.buscarConteudosDoEvento(evento.getId());
        }
        evento.setNomesConteudos(nomesConteudos);

        // registro de detalhes confere se esta habilitado ao completar detalhes do evento
        if (logger.isDebugEnabled()) {
            // gravar historico ao completar detalhes do evento tenta salvar o registro no banco e informa falhas
            try {
                UsuarioRepository.InserirLogsNoBD(null, "DEBUG", EventoService.class.getName(), "preencherDetalhesDoEvento", null,
                        MessageFormatter.arrayFormat("Detalhes carregados eventoId={} turma={} quantidadeConteudos={}",
                                new Object[]{evento.getId(), nomeTurma, nomesConteudos.size()}).getMessage(), null, null);
            } catch (Exception erroLogBD) {
                logger.error("Erro ao gravar log no banco.", erroLogBD);
            }
        }
    }
}

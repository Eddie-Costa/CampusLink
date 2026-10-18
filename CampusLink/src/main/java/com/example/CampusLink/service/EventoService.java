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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.util.List;
import java.io.PrintWriter;
import java.io.StringWriter;

@Service
@RequiredArgsConstructor
public class EventoService {

    @Autowired
    private UsuarioRepository UsuarioRepository;

    private static final Logger logger = LoggerFactory.getLogger(EventoService.class);

    private final EventoRepository eventoRepository;
    private final EventoConteudoRepository eventoConteudoRepository;

    // salva o evento e os conteudos escolhidos
    public Evento adicionarEvento(Evento evento, List<Long> idsConteudos) {

        try {

            Evento eventoSalvo = eventoRepository.save(evento);
            logger.info("Evento salvo no banco id={} professorId={} turmaId={}", eventoSalvo.getId(), eventoSalvo.getIdProfessor(), eventoSalvo.getIdTurma());

            if (logger.isInfoEnabled()) {
                try {
                    UsuarioRepository.InserirLogsNoBD(null, "INFO", EventoService.class.getName(), "adicionarEvento", null,
                            MessageFormatter.arrayFormat("Evento salvo no banco id={} professorId={} turmaId={}", new Object[]{eventoSalvo.getId(), eventoSalvo.getIdProfessor(), eventoSalvo.getIdTurma()}).getMessage(), null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            if (idsConteudos != null && !idsConteudos.isEmpty()) {

                eventoConteudoRepository.associarConteudos(eventoSalvo.getId(), idsConteudos);
                logger.info("Conteudos associados eventoId={} conteudos={}", eventoSalvo.getId(), idsConteudos);

                if (logger.isInfoEnabled()) {
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

            if (logger.isErrorEnabled()) {
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

    // busca os eventos salvos
    public List<Evento> listarEventos() {

        try {

            List<Evento> eventos = eventoRepository.findAll();

            for (Evento evento : eventos) {
                preencherDetalhesDoEvento(evento);
            }

            logger.debug("Eventos encontrados quantidade={}", eventos.size());

            if (logger.isDebugEnabled()) {
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

            if (logger.isErrorEnabled()) {
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

    // busca um evento pelo id para preencher a edicao
    public Evento buscarEventoPorId(Long idEvento) {

        if (idEvento == null) {
            return null;
        }

        Evento evento = eventoRepository.findById(idEvento).orElse(null);

        if (evento != null) {
            preencherDetalhesDoEvento(evento);
        }

        return evento;
    }

    // atualiza o evento e seus conteudos relacionados
    @Transactional
    public Evento atualizarEvento(Long idEvento, Evento dadosAtualizados, List<Long> idsConteudos) {

        if (idEvento == null || dadosAtualizados == null) {
            throw new IllegalArgumentException("Informe os dados do evento");
        }

        if (dadosAtualizados.getNome() == null || dadosAtualizados.getNome().isBlank()
                || dadosAtualizados.getInicio() == null || dadosAtualizados.getFim() == null
                || dadosAtualizados.getIdTurma() == null || dadosAtualizados.getIdProfessor() == null) {
            throw new IllegalArgumentException("Preencha os campos obrigatorios do evento");
        }

        if (dadosAtualizados.getFim().isBefore(dadosAtualizados.getInicio())) {
            throw new IllegalArgumentException("A data final nao pode ser anterior ao inicio");
        }

        try {

            Evento evento = eventoRepository.findById(idEvento)
                    .orElseThrow(() -> new IllegalArgumentException("Evento nao encontrado"));

            // atualiza apenas as informacoes que podem ser editadas
            evento.setNome(dadosAtualizados.getNome().trim());
            evento.setTipo(dadosAtualizados.getTipo());
            evento.setDescricao(dadosAtualizados.getDescricao());
            evento.setInicio(dadosAtualizados.getInicio());
            evento.setFim(dadosAtualizados.getFim());
            evento.setPrioridade(dadosAtualizados.getPrioridade());
            evento.setIdTurma(dadosAtualizados.getIdTurma());
            evento.setIdProfessor(dadosAtualizados.getIdProfessor());

            Evento eventoSalvo = eventoRepository.saveAndFlush(evento);

            // substitui os conteudos antigos pelos selecionados
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

    // apaga o evento e suas ligacoes com os conteudos
    public void excluirEvento(Long idEvento) {

        if (idEvento == null) {
            throw new IllegalArgumentException("O ID do evento nao pode ser nulo");
        }

        try {

            if (!eventoRepository.existsById(idEvento)) {

                logger.warn("Evento nao encontrado eventoId={}", idEvento);

                if (logger.isWarnEnabled()) {
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

            if (logger.isInfoEnabled()) {
                try {
                    UsuarioRepository.InserirLogsNoBD(null, "INFO", EventoService.class.getName(), "excluirEvento", null,
                            MessageFormatter.arrayFormat("Evento excluido eventoId={}", new Object[]{idEvento}).getMessage(), null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

        } catch (DataAccessException | SQLException e) {

            logger.error("Erro ao excluir evento eventoId={}", idEvento, e);

            if (logger.isErrorEnabled()) {
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

    // coloca no evento o nome da turma e dos conteudos
    private void preencherDetalhesDoEvento(Evento evento) {

        if (evento == null) {
            return;
        }

        // mantem o comportamento antigo: sem id, nome nulo e lista vazia
        String nomeTurma = null;
        if (evento.getIdTurma() != null) {
            nomeTurma = eventoRepository.buscarNomeTurma(evento.getIdTurma()).orElse(null);
        }
        evento.setNomeTurma(nomeTurma);

        List<String> nomesConteudos = List.of();
        if (evento.getId() != null) {
            nomesConteudos = eventoRepository.buscarConteudosDoEvento(evento.getId());
        }
        evento.setNomesConteudos(nomesConteudos);

        if (logger.isDebugEnabled()) {
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

package com.example.CampusLink.service;

import com.example.CampusLink.dao.EventoConteudoDAO;
import com.example.CampusLink.dao.EventoDetalhesDAO;
import com.example.CampusLink.model.Evento;
import com.example.CampusLink.repository.EventoRepository;
import com.example.CampusLink.dao.usuarioDAO;

import lombok.RequiredArgsConstructor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.slf4j.helpers.MessageFormatter;
import org.springframework.beans.factory.annotation.Autowired;

import java.sql.SQLException;
import java.util.List;
import java.io.PrintWriter;
import java.io.StringWriter;

@Service
@RequiredArgsConstructor
public class EventoService {

    @Autowired
    private usuarioDAO usuarioDAO;

    private static final Logger logger = LoggerFactory.getLogger(EventoService.class);
    private final EventoRepository eventoRepository;
    private final EventoConteudoDAO eventoConteudoDAO;
    private final EventoDetalhesDAO eventoDetalhesDAO;


    // salva o evento e os conteudos escolhidos
    public Evento adicionarEvento(Evento evento, List<Long> idsConteudos) {

        try {

            Evento eventoSalvo = eventoRepository.save(evento);
            logger.info("Evento salvo no banco id={} professorId={} turmaId={}", eventoSalvo.getId(), eventoSalvo.getIdProfessor(), eventoSalvo.getIdTurma());
            if (logger.isInfoEnabled()) {
                try {
                    usuarioDAO.InserirLogsNoBD(null, "INFO", EventoService.class.getName(), "adicionarEvento", null,
                            MessageFormatter.arrayFormat("Evento salvo no banco id={} professorId={} turmaId={}", new Object[]{eventoSalvo.getId(), eventoSalvo.getIdProfessor(), eventoSalvo.getIdTurma()}).getMessage(), null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            if (idsConteudos != null && !idsConteudos.isEmpty()) {

                eventoConteudoDAO.associarConteudos(eventoSalvo.getId(), idsConteudos);
                logger.info("Conteudos associados eventoId={} conteudos={}", eventoSalvo.getId(), idsConteudos);
                if (logger.isInfoEnabled()) {
                    try {
                        usuarioDAO.InserirLogsNoBD(null, "INFO", EventoService.class.getName(), "adicionarEvento", null,
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
                    usuarioDAO.InserirLogsNoBD(null, "ERROR", EventoService.class.getName(), "adicionarEvento", null,
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
            for (Evento evento : eventos) {preencherDetalhesDoEvento(evento);}
            logger.debug("Eventos encontrados quantidade={}", eventos.size());
            if (logger.isDebugEnabled()) {
                try {
                    usuarioDAO.InserirLogsNoBD(null, "DEBUG", EventoService.class.getName(), "listarEventos", null,
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
                    usuarioDAO.InserirLogsNoBD(null, "ERROR", EventoService.class.getName(), "listarEventos", null,
                            "Erro ao buscar eventos", null, excecaoLogBD.toString());
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
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
                        usuarioDAO.InserirLogsNoBD(null, "WARN", EventoService.class.getName(), "excluirEvento", null,
                                MessageFormatter.arrayFormat("Evento nao encontrado eventoId={}", new Object[]{idEvento}).getMessage(), null, null);
                    } catch (Exception erroLogBD) {
                        logger.error("Erro ao gravar log no banco.", erroLogBD);
                    }
                }
                throw new IllegalArgumentException("Evento nao encontrado");
            }

            eventoConteudoDAO.excluirAssociacoesDoEvento(idEvento);
            eventoRepository.deleteById(idEvento);
            logger.info("Evento excluido eventoId={}", idEvento);
            if (logger.isInfoEnabled()) {
                try {
                    usuarioDAO.InserirLogsNoBD(null, "INFO", EventoService.class.getName(), "excluirEvento", null,
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
                    usuarioDAO.InserirLogsNoBD(null, "ERROR", EventoService.class.getName(), "excluirEvento", null,
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

        String nomeTurma = eventoDetalhesDAO.buscarNomeTurma(evento.getIdTurma());
        evento.setNomeTurma(nomeTurma);
        List<String> nomesConteudos = eventoDetalhesDAO.buscarConteudosDoEvento(evento.getId());
        evento.setNomesConteudos(nomesConteudos);
        logger.debug("Detalhes carregados eventoId={} turma={} quantidadeConteudos={}", evento.getId(), nomeTurma, nomesConteudos.size());
        if (logger.isDebugEnabled()) {
            try {
                usuarioDAO.InserirLogsNoBD(null, "DEBUG", EventoService.class.getName(), "preencherDetalhesDoEvento", null,
                        MessageFormatter.arrayFormat("Detalhes carregados eventoId={} turma={} quantidadeConteudos={}", new Object[]{evento.getId(), nomeTurma, nomesConteudos.size()}).getMessage(), null, null);
            } catch (Exception erroLogBD) {
                logger.error("Erro ao gravar log no banco.", erroLogBD);
            }
        }
    }
}
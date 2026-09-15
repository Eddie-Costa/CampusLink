package com.example.CampusLink.service;

import com.example.CampusLink.dao.EventoConteudoDAO;
import com.example.CampusLink.dao.EventoDetalhesDAO;
import com.example.CampusLink.model.Evento;
import com.example.CampusLink.repository.EventoRepository;

import lombok.RequiredArgsConstructor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EventoService {

    private static final Logger logger = LoggerFactory.getLogger(EventoService.class);
    private final EventoRepository eventoRepository;
    private final EventoConteudoDAO eventoConteudoDAO;
    private final EventoDetalhesDAO eventoDetalhesDAO;


    // salva o evento e os conteudos escolhidos
    public Evento adicionarEvento(Evento evento, List<Long> idsConteudos) {

        try {

            Evento eventoSalvo = eventoRepository.save(evento);
            logger.info("Evento salvo no banco id={} professorId={} turmaId={}", eventoSalvo.getId(), eventoSalvo.getIdProfessor(), eventoSalvo.getIdTurma());

            if (idsConteudos != null && !idsConteudos.isEmpty()) {

                eventoConteudoDAO.associarConteudos(eventoSalvo.getId(), idsConteudos);
                logger.info("Conteudos associados eventoId={} conteudos={}", eventoSalvo.getId(), idsConteudos);
            }

            preencherDetalhesDoEvento(eventoSalvo);
            return eventoSalvo;

        } catch (DataAccessException | SQLException e) {

            logger.error("Erro ao salvar evento professorId={} turmaId={}", evento.getIdProfessor(), evento.getIdTurma(), e);
            throw new RuntimeException("Erro ao salvar evento ou associar conteudos", e);
        }
    }


    // busca os eventos salvos

    public List<Evento> listarEventos() {

        try {

            List<Evento> eventos = eventoRepository.findAll();
            for (Evento evento : eventos) {preencherDetalhesDoEvento(evento);}
            logger.debug("Eventos encontrados quantidade={}", eventos.size());
            return eventos;

        } catch (DataAccessException e) {

            logger.error("Erro ao buscar eventos", e);
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
                throw new IllegalArgumentException("Evento nao encontrado");
            }

            eventoConteudoDAO.excluirAssociacoesDoEvento(idEvento);
            eventoRepository.deleteById(idEvento);
            logger.info("Evento excluido eventoId={}", idEvento);

        } catch (DataAccessException | SQLException e) {

            logger.error("Erro ao excluir evento eventoId={}", idEvento, e);
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
    }
}
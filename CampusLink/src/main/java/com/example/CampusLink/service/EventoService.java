package com.example.CampusLink.service;

import com.example.CampusLink.model.Evento;
import com.example.CampusLink.repository.EventoRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.util.List;

// classe que cuida das regras dos eventos
@Service
@RequiredArgsConstructor
public class EventoService {

    // registra as informacoes do service
    private static final Logger logger =
            LoggerFactory.getLogger(EventoService.class);

    private final EventoRepository eventoRepository;

    // salva um novo evento
    public Evento adicionarEvento(Evento evento) {

        try {
            Evento eventoSalvo = eventoRepository.save(evento);
            logger.info("Evento salvo no banco. id={} professorId={} turmaId={}", eventoSalvo.getId(), eventoSalvo.getIdProfessor(), eventoSalvo.getIdTurma()
            );

            return eventoSalvo;

        } catch (DataAccessException e) {
            logger.error("Erro ao salvar evento. professorId={} turmaId={}", evento.getIdProfessor(), evento.getIdTurma(), e);
            throw e;
        }
    }

    // busca todos os eventos cadastrados
    public List<Evento> listarEventos() {

        try {
            List<Evento> eventos = eventoRepository.findAll();
            logger.debug("Eventos consultados. quantidade={}", eventos.size()
            );
            return eventos;

        } catch (DataAccessException e) {
            logger.error("Erro ao consultar eventos no banco.", e);
            throw e;
        }
    }
}
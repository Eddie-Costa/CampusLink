package com.example.CampusLink.service;

import com.example.CampusLink.model.Disponibilidade;
import com.example.CampusLink.repository.DisponibilidadeRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DisponibilidadeService {

    private static final Logger logger = LoggerFactory.getLogger(DisponibilidadeService.class);
    private final DisponibilidadeRepository disponibilidadeRepository;

    public Disponibilidade salvarDisponibilidade(Long idAluno, LocalDate data, Integer horasDisponiveis) {

        try {
            logger.debug("Buscando disponibilidade existente. alunoId={} data={}", idAluno, data);
            Disponibilidade disponibilidade =
                    disponibilidadeRepository
                            .findByIdAlunoAndData(idAluno, data)
                            .orElseGet(() ->
                                    new Disponibilidade(
                                            idAluno,
                                            data,
                                            horasDisponiveis
                                    )
                            );

            disponibilidade.setHorasDisponiveis(
                    horasDisponiveis
            );

            Disponibilidade disponibilidadeSalva = disponibilidadeRepository.save(disponibilidade);
            logger.info("Disponibilidade salva. id={} alunoId={} data={} horas={}", disponibilidadeSalva.getId(), idAluno, data, horasDisponiveis);
            return disponibilidadeSalva;

        } catch (DataAccessException e) {
            logger.error("Erro ao salvar disponibilidade. alunoId={} data={}", idAluno, data, e);
            throw e;
        }
    }

    public List<Disponibilidade> listarPorAluno(Long idAluno
    ) {
        try {
            List<Disponibilidade> disponibilidades = disponibilidadeRepository.findAllByIdAlunoOrderByDataAsc(idAluno);
            logger.debug("Disponibilidades consultadas. alunoId={} quantidade={}", idAluno, disponibilidades.size());
            return disponibilidades;

        } catch (DataAccessException e) {
            logger.error("Erro ao consultar disponibilidades. alunoId={}", idAluno, e);
            throw e;
        }
    }
}
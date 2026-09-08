package com.example.CampusLink.service;

import com.example.CampusLink.model.Disponibilidade;
import com.example.CampusLink.repository.DisponibilidadeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DisponibilidadeService {

    private final DisponibilidadeRepository disponibilidadeRepository;

    public Disponibilidade salvarDisponibilidade(
            Long idAluno,
            LocalDate data,
            Integer horasDisponiveis
    ) {
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

        disponibilidade.setHorasDisponiveis(horasDisponiveis);

        return disponibilidadeRepository.save(disponibilidade);
    }

    public List<Disponibilidade> listarPorAluno(Long idAluno) {
        return disponibilidadeRepository
                .findAllByIdAlunoOrderByDataAsc(idAluno);
    }
}
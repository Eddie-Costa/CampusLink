// verificacao das horas livres confere que o mesmo dia e atualizado em vez de duplicado
package com.example.CampusLink.service;

import com.example.CampusLink.model.Disponibilidade;
import com.example.CampusLink.repository.DisponibilidadeRepository;
import com.example.CampusLink.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// testes das horas livres esta classe simula o banco das disponibilidades do aluno
class DisponibilidadeServiceTests {

    private static final LocalDate DIA = LocalDate.of(2026, 11, 12);

    private DisponibilidadeRepository repositorio;
    private DisponibilidadeService servico;

    // preparar testes das horas livres este metodo cria o servico com o banco simulado que devolve o que salva
    @BeforeEach
    void preparar() {
        repositorio = mock(DisponibilidadeRepository.class);
        servico = new DisponibilidadeService(repositorio);
        ReflectionTestUtils.setField(servico, "UsuarioRepository", mock(UsuarioRepository.class));
        when(repositorio.save(any())).thenAnswer(chamada -> chamada.getArgument(0));
    }

    // testar dia ja informado este teste confere que as horas do dia existente sao trocadas
    @Test
    void atualizaHorasDeDiaJaInformado() {
        // preparar dia com duas horas
        Disponibilidade existente = new Disponibilidade(7L, DIA, 2);
        when(repositorio.findByIdAlunoAndData(7L, DIA)).thenReturn(Optional.of(existente));

        // executar novo registro do mesmo dia
        Disponibilidade salva = servico.salvarDisponibilidade(7L, DIA, 5);

        // verificar mesmo registro com as novas horas
        assertSame(existente, salva);
        assertEquals(5, salva.getHorasDisponiveis());
    }

    // testar dia novo este teste confere a criacao do registro e a listagem do aluno
    @Test
    void criaDiaNovoEListaDoAluno() {
        // preparar aluno sem registro no dia
        when(repositorio.findByIdAlunoAndData(7L, DIA)).thenReturn(Optional.empty());
        when(repositorio.findAllByIdAlunoOrderByDataAsc(7L)).thenReturn(List.of(new Disponibilidade(7L, DIA, 3)));

        // executar registro e listagem
        Disponibilidade salva = servico.salvarDisponibilidade(7L, DIA, 3);
        List<Disponibilidade> lista = servico.listarPorAluno(7L);

        // verificar dados
        assertEquals(DIA, salva.getData());
        assertEquals(1, lista.size());
    }

    // testar falha do banco este teste confere que o erro do banco chega ao controlador nas duas operacoes
    @Test
    void repassaFalhasDoBanco() {
        // preparar banco indisponivel
        DataRetrievalFailureException falha = new DataRetrievalFailureException("banco");
        when(repositorio.findByIdAlunoAndData(7L, DIA)).thenThrow(falha);
        when(repositorio.findAllByIdAlunoOrderByDataAsc(7L)).thenThrow(falha);

        // executar operacoes
        DataRetrievalFailureException salvar = assertThrows(DataRetrievalFailureException.class, () -> servico.salvarDisponibilidade(7L, DIA, 3));
        DataRetrievalFailureException listar = assertThrows(DataRetrievalFailureException.class, () -> servico.listarPorAluno(7L));

        // verificar erro original
        assertSame(falha, salvar);
        assertSame(falha, listar);
    }
}

package com.example.CampusLink.service;

import com.example.CampusLink.dao.usuarioDAO;
import com.example.CampusLink.model.Disponibilidade;
import com.example.CampusLink.repository.DisponibilidadeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DisponibilidadeServiceTest {

    @Test
    void deveRetornarListaVaziaQuandoAlunoNaoPossuiDisponibilidades() {
        // Arrange
        when(disponibilidadeRepository.findAllByIdAlunoOrderByDataAsc(20L)).thenReturn(List.of());
        // Act
        var resultado = disponibilidadeService.listarPorAluno(20L);
        // Assert
        assertEquals(List.of(), resultado);
        verify(disponibilidadeRepository).findAllByIdAlunoOrderByDataAsc(20L);
    }

    @Mock
    private DisponibilidadeRepository disponibilidadeRepository;

    @Mock
    private usuarioDAO usuarios;

    private DisponibilidadeService disponibilidadeService;

    @BeforeEach
    void preparar() {
        disponibilidadeService = new DisponibilidadeService(disponibilidadeRepository);
        // Isola também a gravação de logs no banco, sem iniciar o Spring.
        ReflectionTestUtils.setField(disponibilidadeService, "usuarioDAO", usuarios);
    }

    @Test
    void deveCriarDisponibilidadeQuandoAlunoEDataNaoPossuemRegistro() {
        // Arrange: simula a ausência de registro para este aluno e esta data.
        Long idAluno = 20L;
        LocalDate data = LocalDate.of(2026, 10, 22);
        Integer horasDisponiveis = 4;
        when(disponibilidadeRepository.findByIdAlunoAndData(idAluno, data))
                .thenReturn(Optional.empty());

        Disponibilidade disponibilidadeSalva = new Disponibilidade(idAluno, data, horasDisponiveis);
        // Simula o ID gerado pelo banco na instância retornada pelo repositório.
        ReflectionTestUtils.setField(disponibilidadeSalva, "id", 10L);
        when(disponibilidadeRepository.save(any(Disponibilidade.class)))
                .thenReturn(disponibilidadeSalva);

        // Act: o serviço deve construir e salvar uma nova disponibilidade.
        Disponibilidade resultado = disponibilidadeService.salvarDisponibilidade(
                idAluno, data, horasDisponiveis
        );

        // Assert: captura os dados enviados para salvar, além de conferir o retorno.
        ArgumentCaptor<Disponibilidade> captor = ArgumentCaptor.forClass(Disponibilidade.class);
        verify(disponibilidadeRepository).findByIdAlunoAndData(idAluno, data);
        verify(disponibilidadeRepository).save(captor.capture());
        Disponibilidade enviadaParaSalvar = captor.getValue();

        assertAll(
                () -> assertNull(enviadaParaSalvar.getId()),
                () -> assertEquals(idAluno, enviadaParaSalvar.getIdAluno()),
                () -> assertEquals(data, enviadaParaSalvar.getData()),
                () -> assertEquals(horasDisponiveis, enviadaParaSalvar.getHorasDisponiveis()),
                () -> assertSame(disponibilidadeSalva, resultado)
        );
    }

    @Test
    void deveAtualizarDisponibilidadeExistenteParaMesmoAlunoEData() {
        // Arrange: simula um registro existente com duas horas disponíveis.
        Long idAluno = 20L;
        LocalDate data = LocalDate.of(2026, 10, 22);
        Integer novasHoras = 6;
        Disponibilidade existente = new Disponibilidade(idAluno, data, 2);
        ReflectionTestUtils.setField(existente, "id", 10L);
        when(disponibilidadeRepository.findByIdAlunoAndData(idAluno, data))
                .thenReturn(Optional.of(existente));

        Disponibilidade disponibilidadeSalva = new Disponibilidade(idAluno, data, novasHoras);
        ReflectionTestUtils.setField(disponibilidadeSalva, "id", 10L);
        when(disponibilidadeRepository.save(any(Disponibilidade.class)))
                .thenReturn(disponibilidadeSalva);

        // Act: solicita a alteração das horas para o mesmo aluno e a mesma data.
        Disponibilidade resultado = disponibilidadeService.salvarDisponibilidade(
                idAluno, data, novasHoras
        );

        // Assert: salva a instância existente, preservando sua identidade e alterando as horas.
        ArgumentCaptor<Disponibilidade> captor = ArgumentCaptor.forClass(Disponibilidade.class);
        verify(disponibilidadeRepository).findByIdAlunoAndData(idAluno, data);
        verify(disponibilidadeRepository).save(captor.capture());
        Disponibilidade enviadaParaSalvar = captor.getValue();

        assertAll(
                () -> assertSame(existente, enviadaParaSalvar),
                () -> assertEquals(10L, enviadaParaSalvar.getId()),
                () -> assertEquals(idAluno, enviadaParaSalvar.getIdAluno()),
                () -> assertEquals(data, enviadaParaSalvar.getData()),
                () -> assertEquals(novasHoras, enviadaParaSalvar.getHorasDisponiveis()),
                () -> assertSame(disponibilidadeSalva, resultado)
        );
    }

    @Test
    void devePropagarExcecaoQuandoFalharAoSalvarDisponibilidade() {
        // Arrange: a consulta funciona, mas a tentativa de salvar falha.
        Long idAluno = 20L;
        LocalDate data = LocalDate.of(2026, 10, 22);
        when(disponibilidadeRepository.findByIdAlunoAndData(idAluno, data))
                .thenReturn(Optional.empty());
        DataAccessResourceFailureException falha = new DataAccessResourceFailureException(
                "Falha simulada ao salvar disponibilidade"
        );
        when(disponibilidadeRepository.save(any(Disponibilidade.class))).thenThrow(falha);

        // Act: tenta salvar e captura a exceção, verificando seu tipo.
        DataAccessResourceFailureException erro = assertThrows(
                DataAccessResourceFailureException.class,
                () -> disponibilidadeService.salvarDisponibilidade(idAluno, data, 4)
        );

        // Assert: preserva a exceção original e a mensagem recebida do repositório.
        assertAll(
                () -> assertSame(falha, erro),
                () -> assertEquals("Falha simulada ao salvar disponibilidade", erro.getMessage())
        );
        verify(disponibilidadeRepository).findByIdAlunoAndData(idAluno, data);
        verify(disponibilidadeRepository).save(any(Disponibilidade.class));
    }
}

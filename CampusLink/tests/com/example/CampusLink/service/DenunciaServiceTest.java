package com.example.CampusLink.service;

import com.example.CampusLink.dao.*;
import com.example.CampusLink.dto.DenunciaDTO;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import java.sql.SQLException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DenunciaServiceTest {
    @Mock denunciaDAO denuncias;
    @Mock conteudoDAO conteudos;
    @Mock usuarioDAO usuarios;
    DenunciaService service;

    @BeforeEach void preparar() {
        service = new DenunciaService(denuncias, conteudos);
        ReflectionTestUtils.setField(service, "usuarioDAO", usuarios);
    }

    private void conteudoValido(int alunos, int quantidade) throws Exception {
        when(conteudos.buscarStatus(10L)).thenReturn("ativo");
        when(conteudos.buscarIdProfessor(10L)).thenReturn(40L);
        when(denuncias.contarAlunosDaTurma(30L)).thenReturn(alunos);
        when(denuncias.contarDenunciasPendentes(10L)).thenReturn(quantidade);
    }

    @Test void deveRegistrarDenunciaComMotivoSemEspacosNasExtremidades() throws Exception {
        // Arrange
        conteudoValido(20, 1);
        // Act
        String resultado = service.registrarDenuncia(10L, 20L, 30L, "  Conteúdo incorreto  ");
        // Assert
        assertEquals("sucesso", resultado);
        ArgumentCaptor<DenunciaDTO> captor = ArgumentCaptor.forClass(DenunciaDTO.class);
        verify(denuncias).inserir(captor.capture());
        var denuncia = captor.getValue();
        assertAll(() -> assertEquals(10L, denuncia.getIdConteudo()),
                () -> assertEquals(20L, denuncia.getIdAluno()),
                () -> assertEquals(40L, denuncia.getIdProfessor()),
                () -> assertEquals("Conteúdo incorreto", denuncia.getMotivo()));
        verify(conteudos, never()).atualizarStatus(any(), any());
    }

    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {" ", "\t"})
    void deveRecusarMotivoAusenteSemPersistir(String motivo) throws Exception {
        String resultado = service.registrarDenuncia(10L, 20L, 30L, motivo);
        assertEquals("motivo_vazio", resultado);
        verifyNoInteractions(denuncias, conteudos);
    }

    @ParameterizedTest
    @CsvSource({"10,1,sucesso", "10,2,suspenso", "20,2,sucesso", "20,3,suspenso",
            "21,3,sucesso", "21,4,suspenso", "1,1,sucesso", "1,2,suspenso"})
    void deveAplicarLimiteDeQuinzePorCentoComMinimoDeDuasDenuncias(int alunos, int quantidade, String esperado) throws Exception {
        conteudoValido(alunos, quantidade);
        String resultado = service.registrarDenuncia(10L, 20L, 30L, "Motivo");
        assertEquals(esperado, resultado);
        verify(denuncias).inserir(any(DenunciaDTO.class));
        if (esperado.equals("suspenso")) verify(conteudos).atualizarStatus(10L, "suspenso_denuncia");
        else verify(conteudos, never()).atualizarStatus(any(), any());
    }

    @Test void deveRecusarDenunciaDuplicada() throws Exception {
        when(conteudos.buscarStatus(10L)).thenReturn("ativo");
        when(denuncias.alunoJaDenunciou(10L, 20L)).thenReturn(true);
        assertEquals("duplicada", service.registrarDenuncia(10L, 20L, 30L, "Motivo"));
        verify(denuncias, never()).inserir(any());
    }

    @ParameterizedTest @CsvSource(value = {"NULL,20,30", "10,NULL,30", "10,20,NULL"}, nullValues = "NULL")
    void deveRecusarIdentificadoresAusentes(Long conteudo, Long aluno, Long turma) {
        assertEquals("erro", service.registrarDenuncia(conteudo, aluno, turma, "Motivo"));
        verifyNoInteractions(denuncias, conteudos);
    }

    @ParameterizedTest @NullSource @ValueSource(strings = {"removido", "suspenso_denuncia"})
    void deveRecusarConteudoInexistenteOuIndisponivel(String status) throws Exception {
        when(conteudos.buscarStatus(10L)).thenReturn(status);
        assertEquals(status == null ? "conteudo_nao_encontrado" : "conteudo_indisponivel",
                service.registrarDenuncia(10L, 20L, 30L, "Motivo"));
        verifyNoInteractions(denuncias);
    }

    @Test void deveRecusarTurmaSemAlunos() throws Exception {
        when(conteudos.buscarStatus(10L)).thenReturn("ativo");
        when(conteudos.buscarIdProfessor(10L)).thenReturn(40L);
        when(denuncias.contarAlunosDaTurma(30L)).thenReturn(0);
        assertEquals("turma_sem_alunos", service.registrarDenuncia(10L, 20L, 30L, "Motivo"));
        verify(denuncias, never()).inserir(any());
    }

    @Test void devePreservarCausaAoFalharRegistro() throws Exception {
        SQLException causa = new SQLException("falha simulada");
        when(conteudos.buscarStatus(10L)).thenThrow(causa);
        var erro = assertThrows(RuntimeException.class, () -> service.registrarDenuncia(10L, 20L, 30L, "Motivo"));
        assertEquals("Não foi possível registrar a denúncia", erro.getMessage());
        assertSame(causa, erro.getCause());
        verify(denuncias, never()).inserir(any());
    }

    @Test void deveRecusarConteudoSemProfessorResponsavel() throws Exception {
        when(conteudos.buscarStatus(10L)).thenReturn("ativo");
        when(conteudos.buscarIdProfessor(10L)).thenReturn(null);
        assertEquals("professor_nao_encontrado", service.registrarDenuncia(10L, 20L, 30L, "Motivo"));
        verify(denuncias, never()).inserir(any());
    }

    @Test void deveConsultarDenunciasDoConteudo() throws Exception {
        var dto = new DenunciaDTO(); dto.setIdConteudo(10L);
        var lista = java.util.List.of(dto); when(denuncias.listarPorConteudo(10L)).thenReturn(lista);
        assertSame(lista, service.listarPorConteudo(10L)); verify(denuncias).listarPorConteudo(10L);
    }

    @Test void deveContarDenunciasPendentes() throws Exception {
        when(denuncias.contarDenunciasPendentes(10L)).thenReturn(3);
        assertEquals(3, service.contarDenuncias(10L)); verify(denuncias).contarDenunciasPendentes(10L);
    }

    @Test void deveListarNomesDosAlunosDenunciantes() throws Exception {
        when(denuncias.listarNomesAlunosPorConteudo(10L)).thenReturn(java.util.List.of("Ana", "Bruno"));
        assertEquals(java.util.List.of("Ana", "Bruno"), service.listarNomesAlunos(10L));
    }
}

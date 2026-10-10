package com.example.CampusLink.service;

import com.example.CampusLink.dao.conteudoDAO;
import com.example.CampusLink.dao.usuarioDAO;
import com.example.CampusLink.dto.ConteudoDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class ConteudoServiceTests {

    private conteudoDAO conteudos;
    private ArquivoService arquivos;
    private ConteudoService service;

    @BeforeEach
    void preparar() {
        conteudos = mock(conteudoDAO.class);
        arquivos = mock(ArquivoService.class);
        service = new ConteudoService(conteudos, arquivos);
        ReflectionTestUtils.setField(service, "usuarioDAO", mock(usuarioDAO.class));
    }

    @Test
    void criaConteudoDeTextoComPrioridadePadrao() throws Exception {
        ConteudoDTO conteudo = new ConteudoDTO();
        conteudo.setTitulo("Introdução à engenharia de software");
        conteudo.setIdTurma(12L);
        conteudo.setIdProfessor(34L);

        ConteudoDTO resultado = service.criar(conteudo, null);

        assertSame(conteudo, resultado);
        assertEquals("media", conteudo.getPrioridade());
        assertEquals("texto", conteudo.getTipo());
        verify(conteudos).inserir(conteudo);
        verifyNoInteractions(arquivos);
    }

    @Test
    void falhaAoPersistirConteudoLancaExcecaoComMensagemEDetalheOriginais() throws Exception {
        ConteudoDTO conteudo = new ConteudoDTO();
        conteudo.setTitulo("Introdução à engenharia de software");
        SQLException falhaBanco = new SQLException("banco indisponível");
        doThrow(falhaBanco).when(conteudos).inserir(conteudo);

        RuntimeException excecao = assertThrows(
                RuntimeException.class,
                () -> service.criar(conteudo, null));

        assertEquals("Não foi possível cadastrar o conteúdo.", excecao.getMessage());
        assertInstanceOf(SQLException.class, excecao.getCause());
        assertEquals("banco indisponível", excecao.getCause().getMessage());
        verify(conteudos).inserir(conteudo);
        verifyNoInteractions(arquivos);
    }

    @ParameterizedTest
    @CsvSource({"'https://example.org/material', link", "'', texto"})
    void defineTipoDoConteudoConformePresencaDaUrl(String url, String tipoEsperado) throws Exception {
        ConteudoDTO conteudo = new ConteudoDTO();
        conteudo.setTitulo("Material de engenharia de software");
        conteudo.setUrl(url);

        service.criar(conteudo, null);

        assertEquals(tipoEsperado, conteudo.getTipo());
        verify(conteudos).inserir(conteudo);
    }

    @Test
    void falhaNoUploadRemoveConteudoPersistidoERelancaErroOriginal() throws Exception {
        ConteudoDTO conteudo = new ConteudoDTO();
        conteudo.setId(56L);
        conteudo.setTitulo("Apostila de engenharia de software");
        MultipartFile arquivo = mock(MultipartFile.class);
        RuntimeException falhaStorage = new RuntimeException("armazenamento indisponível");
        org.mockito.Mockito.when(arquivo.isEmpty()).thenReturn(false);
        org.mockito.Mockito.doThrow(falhaStorage).when(arquivos).salvar(arquivo, 56L);

        RuntimeException excecao = assertThrows(
                RuntimeException.class,
                () -> service.criar(conteudo, arquivo));

        assertSame(falhaStorage, excecao);
        assertEquals("armazenamento indisponível", excecao.getMessage());
        verify(conteudos).inserir(conteudo);
        verify(conteudos).excluir(56L);
        verify(arquivos).salvar(arquivo, 56L);
    }
}

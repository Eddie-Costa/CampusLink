// verificacao dos arquivos dos materiais confere validacao do envio registro no banco download e exclusao
package com.example.CampusLink.service;

import com.example.CampusLink.dto.ArquivoDTO;
import com.example.CampusLink.repository.ArquivoRepository;
import com.example.CampusLink.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// testes dos arquivos esta classe simula o banco e o armazenamento do supabase
class ArquivoServiceTests {

    private ArquivoRepository repositorio;
    private SupabaseStorageService storage;
    private ArquivoService arquivos;

    // preparar testes dos arquivos este metodo cria o servico com banco armazenamento e logs simulados
    @BeforeEach
    void preparar() {
        repositorio = mock(ArquivoRepository.class);
        storage = mock(SupabaseStorageService.class);
        arquivos = new ArquivoService(repositorio, storage, "materiais");
        ReflectionTestUtils.setField(arquivos, "UsuarioRepository", mock(UsuarioRepository.class));
    }

    // testar envio aceito este teste confere o arquivo enviado ao armazenamento e registrado no banco
    @Test
    void enviaERegistraArquivoPermitido() throws SQLException {
        // preparar pdf valido
        MockMultipartFile pdf = new MockMultipartFile("arquivo", "Aula.PDF", "application/pdf", new byte[]{1, 2, 3});
        when(storage.upload(pdf)).thenReturn("abc.pdf");

        // executar envio
        arquivos.salvar(pdf, 10L);

        // verificar dados registrados
        ArgumentCaptor<ArquivoDTO> registro = ArgumentCaptor.forClass(ArquivoDTO.class);
        verify(repositorio).inserir(registro.capture());
        assertEquals("materiais", registro.getValue().getBucketId());
        assertEquals("abc.pdf", registro.getValue().getStoragePath());
        assertEquals(3L, registro.getValue().getTamanhoBytes());
        assertEquals(10L, registro.getValue().getIdConteudo());
    }

    // testar envio recusado este teste confere arquivo vazio grande demais extensao proibida e tipo falso
    @Test
    void recusaArquivosForaDasRegras() {
        // preparar arquivos invalidos
        MultipartFile grande = mock(MultipartFile.class);
        when(grande.getSize()).thenReturn(50L * 1024 * 1024 + 1);
        MockMultipartFile vazio = new MockMultipartFile("arquivo", "aula.pdf", "application/pdf", new byte[0]);
        MockMultipartFile executavel = new MockMultipartFile("arquivo", "virus.exe", "application/pdf", new byte[]{1});
        MockMultipartFile semExtensao = new MockMultipartFile("arquivo", "aula.", "application/pdf", new byte[]{1});
        MockMultipartFile tipoFalso = new MockMultipartFile("arquivo", "pagina.pdf", "text/html", new byte[]{1});

        // executar envios e verificar a mensagem de cada recusa
        assertEquals("Selecione um arquivo.", recusa(vazio));
        assertEquals("Selecione um arquivo.", recusa(null));
        assertTrue(recusa(grande).contains("50 MB"));
        assertTrue(recusa(executavel).startsWith("Extensão de arquivo não permitida"));
        assertTrue(recusa(semExtensao).startsWith("Extensão de arquivo não permitida"));
        assertEquals("Tipo de arquivo não permitido.", recusa(tipoFalso));
        verify(storage, never()).upload(any());
    }

    // testar falha no registro este teste confere que o arquivo enviado e apagado quando o banco falha
    @Test
    void apagaArquivoEnviadoQuandoBancoFalha() throws SQLException {
        // preparar banco indisponivel apos o envio
        MockMultipartFile pdf = new MockMultipartFile("arquivo", "aula.pdf", "application/pdf", new byte[]{1});
        when(storage.upload(pdf)).thenReturn("abc.pdf");
        doThrow(new SQLException("banco")).when(repositorio).inserir(any());

        // executar envio
        RuntimeException erro = assertThrows(RuntimeException.class, () -> arquivos.salvar(pdf, 10L));

        // verificar remocao do arquivo orfao e mensagem
        verify(storage).excluir("abc.pdf");
        assertTrue(erro.getMessage().contains("registrar o arquivo"));
    }

    // testar falha no armazenamento este teste confere que nada e registrado quando o envio falha
    @Test
    void naoRegistraQuandoArmazenamentoFalha() throws SQLException {
        // preparar armazenamento indisponivel
        MockMultipartFile pdf = new MockMultipartFile("arquivo", "aula.pdf", "application/pdf", new byte[]{1});
        when(storage.upload(pdf)).thenThrow(new IllegalStateException("fora do ar"));

        // executar envio
        assertThrows(IllegalStateException.class, () -> arquivos.salvar(pdf, 10L));

        // verificar que o banco nao foi usado
        verify(repositorio, never()).inserir(any());
    }

    // testar download e exclusao este teste confere o caminho usado no armazenamento e a remocao do registro
    @Test
    void baixaEExcluiPeloCaminhoGuardado() throws SQLException {
        // preparar arquivo registrado
        ArquivoDTO arquivo = new ArquivoDTO();
        arquivo.setStoragePath("abc.pdf");
        when(repositorio.buscarPorId(1L)).thenReturn(arquivo);
        when(storage.download("abc.pdf")).thenReturn(new byte[]{9});

        // executar download e exclusao
        byte[] dados = arquivos.download(1L);
        arquivos.excluir(1L);

        // verificar conteudo e exclusao nos dois lugares
        assertArrayEquals(new byte[]{9}, dados);
        verify(storage).excluir("abc.pdf");
        verify(repositorio).excluir(1L);
    }

    // testar arquivo inexistente e falhas este teste confere as mensagens de arquivo ausente e de falhas do banco
    @Test
    void informaArquivoAusenteEFalhasDoBanco() throws SQLException {
        // preparar banco sem o arquivo 9 e com falhas nas demais consultas
        ArquivoDTO arquivo = new ArquivoDTO();
        arquivo.setStoragePath("abc.pdf");
        when(repositorio.buscarPorId(1L)).thenReturn(arquivo);
        doThrow(new SQLException("banco")).when(repositorio).excluir(1L);
        when(repositorio.buscarPorId(2L)).thenThrow(new SQLException("banco"));
        when(repositorio.listarTodos()).thenThrow(new SQLException("banco"));
        when(repositorio.buscarMaisRecentePorConteudo(10L)).thenThrow(new SQLException("banco"));
        when(storage.download("abc.pdf")).thenThrow(new IllegalStateException("sem arquivo"));

        // executar consultas e verificar mensagens
        assertEquals("Arquivo não encontrado.", assertThrows(IllegalArgumentException.class, () -> arquivos.buscarPorId(9L)).getMessage());
        assertTrue(assertThrows(RuntimeException.class, () -> arquivos.excluir(1L)).getMessage().contains("removido do Storage"));
        assertEquals("Erro ao buscar o arquivo.", assertThrows(RuntimeException.class, () -> arquivos.buscarPorId(2L)).getMessage());
        assertEquals("Não foi possível listar os arquivos.", assertThrows(RuntimeException.class, arquivos::listarTodos).getMessage());
        assertTrue(assertThrows(RuntimeException.class, () -> arquivos.buscarMaisRecentePorConteudo(10L)).getMessage().contains("conteúdo"));
        assertThrows(IllegalStateException.class, () -> arquivos.download(1L));
    }

    // testar falha ao apagar no armazenamento este teste confere que o registro fica no banco se o arquivo nao saiu do armazenamento
    @Test
    void mantemRegistroQuandoArmazenamentoNaoApaga() throws SQLException {
        // preparar armazenamento que falha na exclusao
        ArquivoDTO arquivo = new ArquivoDTO();
        arquivo.setStoragePath("abc.pdf");
        when(repositorio.buscarPorId(1L)).thenReturn(arquivo);
        doThrow(new IllegalStateException("fora do ar")).when(storage).excluir("abc.pdf");

        // executar exclusao
        assertThrows(IllegalStateException.class, () -> arquivos.excluir(1L));

        // verificar que o registro continua no banco
        verify(repositorio, never()).excluir(1L);
    }

    // obter mensagem de recusa este metodo tenta enviar o arquivo e devolve o motivo da recusa
    private String recusa(MultipartFile arquivo) {
        return assertThrows(IllegalArgumentException.class, () -> arquivos.salvar(arquivo, 10L)).getMessage();
    }
}

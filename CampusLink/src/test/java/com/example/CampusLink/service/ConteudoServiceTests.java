// verificacao das regras dos materiais confere tipo duracao busca envio de arquivo e mensagens de falha do banco
package com.example.CampusLink.service;

import com.example.CampusLink.dto.ConteudoDTO;
import com.example.CampusLink.repository.ConteudoRepository;
import com.example.CampusLink.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// testes das regras dos materiais esta classe simula o banco e o envio de arquivos
class ConteudoServiceTests {

    private ConteudoRepository repositorio;
    private ArquivoService arquivos;
    private ConteudoService conteudos;

    // preparar testes dos materiais este metodo cria o servico com banco envio de arquivos e logs simulados
    @BeforeEach
    void preparar() throws SQLException {
        repositorio = mock(ConteudoRepository.class);
        arquivos = mock(ArquivoService.class);
        conteudos = new ConteudoService(repositorio, arquivos);
        ReflectionTestUtils.setField(conteudos, "UsuarioRepository", mock(UsuarioRepository.class));
        doAnswer(chamada -> {
            chamada.<ConteudoDTO>getArgument(0).setId(40L);
            return null;
        }).when(repositorio).inserir(any());
    }

    // testar conversao da duracao este teste confere os formatos aceitos para o tempo estimado do material
    @ParameterizedTest
    @CsvSource(value = {"45,45", "1:30:00,90", "12:30,13", "2h,120", "3 horas,180", "30 min,30", "15 minutos,15",
            "'  ',"}, ignoreLeadingAndTrailingWhitespace = false)
    void converteDuracaoEmMinutos(String texto, Integer esperado) {
        // preparar material com a duracao informada
        ConteudoDTO conteudo = conteudo(texto);

        // executar cadastro
        conteudos.criar(conteudo, null);

        // verificar minutos calculados
        assertEquals(esperado, conteudo.getDuracaoEstimadaMin());
    }

    // testar duracao invalida este teste confere que zero e texto sem numero sao recusados antes de gravar
    @ParameterizedTest
    @ValueSource(strings = {"0", "meia hora"})
    void recusaDuracaoInvalida(String texto) {
        // preparar material com duracao invalida
        ConteudoDTO conteudo = conteudo(texto);

        // executar cadastro e verificar recusa
        assertThrows(IllegalArgumentException.class, () -> conteudos.criar(conteudo, null));
    }

    // testar tipo do material este teste confere arquivo link e texto e a prioridade media padrao
    @Test
    void defineTipoEPrioridadePadrao() {
        // preparar material com arquivo material com link e material so com texto
        MockMultipartFile pdf = new MockMultipartFile("arquivo", "aula.pdf", "application/pdf", new byte[]{1});
        ConteudoDTO comArquivo = conteudo(null);
        ConteudoDTO comLink = conteudo(null);
        comLink.setUrl("https://www.youtube.com/watch?v=1");
        ConteudoDTO soTexto = conteudo(null);
        soTexto.setUrl(" ");

        // executar cadastros
        conteudos.criar(comArquivo, pdf);
        conteudos.criar(comLink, new MockMultipartFile("arquivo", new byte[0]));
        conteudos.criar(soTexto, null);

        // verificar tipos prioridade e envio do arquivo para o material criado
        assertEquals("arquivo", comArquivo.getTipo());
        assertEquals("link", comLink.getTipo());
        assertEquals("texto", soTexto.getTipo());
        assertEquals("media", soTexto.getPrioridade());
        verify(arquivos).salvar(pdf, 40L);
    }

    // testar falha no envio este teste confere que o material e desfeito quando o arquivo nao e salvo
    @Test
    void desfazMaterialQuandoArquivoNaoEhSalvo() throws SQLException {
        // preparar armazenamento indisponivel
        MockMultipartFile pdf = new MockMultipartFile("arquivo", "aula.pdf", "application/pdf", new byte[]{1});
        RuntimeException falha = new IllegalStateException("armazenamento fora do ar");
        doThrow(falha).when(arquivos).salvar(any(), anyLong());

        // executar cadastro
        RuntimeException erro = assertThrows(RuntimeException.class, () -> conteudos.criar(conteudo(null), pdf));

        // verificar exclusao do material e erro original
        assertSame(falha, erro);
        verify(repositorio).excluir(40L);
    }

    // testar falha dupla este teste confere que o erro do envio continua sendo informado mesmo se a exclusao falhar
    @Test
    void mantemErroDoEnvioQuandoExclusaoTambemFalha() throws SQLException {
        // preparar envio e exclusao com falha
        MockMultipartFile pdf = new MockMultipartFile("arquivo", "aula.pdf", "application/pdf", new byte[]{1});
        doThrow(new IllegalStateException("envio")).when(arquivos).salvar(any(), anyLong());
        doThrow(new SQLException("exclusao")).when(repositorio).excluir(40L);

        // executar cadastro
        IllegalStateException erro = assertThrows(IllegalStateException.class, () -> conteudos.criar(conteudo(null), pdf));

        // verificar erro do envio
        assertEquals("envio", erro.getMessage());
    }

    // testar limpeza da busca este teste confere o texto cortado em cem letras e o tipo invalido ignorado
    @Test
    void limpaTextoETipoDaBusca() throws SQLException {
        // preparar texto muito longo
        String longo = "  " + "a".repeat(150) + "  ";

        // executar buscas
        conteudos.buscarPorTurma(1L, longo, "PDF");
        conteudos.buscarPorTurma(1L, null, "VIDEO");

        // verificar texto e tipos enviados ao banco
        verify(repositorio).buscarPorTurma(1L, "a".repeat(100), null);
        verify(repositorio).buscarPorTurma(1L, null, "video");
    }

    // testar acoes do professor este teste confere que as respostas do banco chegam ao controlador
    @Test
    void repassaResultadoDasAcoesDoProfessor() throws SQLException {
        // preparar remocao aceita e reabertura e revisao recusadas
        when(repositorio.removerConteudoProfessor(10L, 4L)).thenReturn(true);

        // executar acoes
        boolean removeu = conteudos.removerConteudoProfessor(10L, 4L);
        boolean reabriu = conteudos.reabrir(10L, 4L, 1L);
        boolean pediu = conteudos.solicitarRevisao(10L, 4L);
        when(repositorio.reabrir(10L, 4L, 1L)).thenReturn(true);
        when(repositorio.solicitarRevisao(10L, 4L)).thenReturn(true);

        // verificar respostas e nova tentativa aceita
        assertTrue(removeu);
        assertFalse(reabriu);
        assertFalse(pediu);
        assertTrue(conteudos.reabrir(10L, 4L, 1L));
        assertTrue(conteudos.solicitarRevisao(10L, 4L));
    }

    // testar edicao e conclusao este teste confere o tipo recalculado na edicao e a conclusao dentro do prazo
    @Test
    void atualizaMaterialEConcluiDentroDoPrazo() throws SQLException {
        // preparar material editado para link e conteudo da turma sem prazo vencido
        ConteudoDTO editado = conteudo("1h");
        editado.setUrl("https://site.com");
        when(repositorio.conteudoPertenceTurma(10L, 1L)).thenReturn(true);
        when(repositorio.listarPrazosEncerrados(any(), any())).thenReturn(Map.of());

        // executar edicao e conclusao
        conteudos.atualizar(editado);
        conteudos.alterarConclusao(7L, 1L, 10L, true);

        // verificar tipo minutos e conclusao gravada
        assertEquals("link", editado.getTipo());
        assertEquals(60, editado.getDuracaoEstimadaMin());
        verify(repositorio).marcarConcluido(7L, 10L);
        verify(repositorio, never()).desmarcarConcluido(anyLong(), anyLong());
    }

    // testar falhas do banco este teste confere que cada operacao troca o erro tecnico por uma mensagem para o usuario
    @Test
    void trocaFalhasDoBancoPorMensagensAmigaveis() {
        // preparar banco que falha em qualquer operacao
        ConteudoService semBanco = new ConteudoService(mock(ConteudoRepository.class, chamada -> {
            throw new SQLException("banco fora do ar");
        }), arquivos);
        ReflectionTestUtils.setField(semBanco, "UsuarioRepository", mock(UsuarioRepository.class));
        Map<String, Executable> operacoes = new LinkedHashMap<>();
        operacoes.put("cadastrar o conteúdo", () -> semBanco.criar(conteudo(null), null));
        operacoes.put("listar os conteúdos da turma", () -> semBanco.listarPorTurma(1L));
        operacoes.put("buscar os conteúdos", () -> semBanco.buscarPorTurma(1L, "a", "link"));
        operacoes.put("conteúdos do professor", () -> semBanco.listarPorProfessor(4L));
        operacoes.put("enviados para análise", semBanco::listarParaAnaliseAdmin);
        operacoes.put("remover o conteúdo", () -> semBanco.removerConteudoProfessor(10L, 4L));
        operacoes.put("re-disponibilizar", () -> semBanco.reabrir(10L, 4L, 1L));
        operacoes.put("solicitar a análise", () -> semBanco.solicitarRevisao(10L, 4L));
        operacoes.put("verificar a turma", () -> semBanco.pertenceATurma(10L, 1L));
        operacoes.put("excluir o conteúdo", () -> semBanco.excluir(10L));
        operacoes.put("atualizar o conteúdo", () -> semBanco.atualizar(conteudo(null)));
        operacoes.put("atualizar o progresso", () -> semBanco.alterarConclusao(7L, 1L, 10L, true));
        operacoes.put("conteúdos concluídos", () -> semBanco.listarConcluidosDoAluno(7L, 1L));
        operacoes.put("verificar os prazos", () -> semBanco.listarPrazosEncerrados(List.of(10L)));

        // executar cada operacao e verificar a mensagem correspondente
        operacoes.forEach((mensagem, operacao) -> {
            RuntimeException erro = assertThrows(RuntimeException.class, operacao, mensagem);
            assertTrue(erro.getMessage().contains(mensagem), erro.getMessage());
            assertTrue(erro.getCause() instanceof SQLException, mensagem);
        });
    }

    // testar consultas paginadas este teste confere que as consultas de paginas e totais sao repassadas ao banco
    @Test
    void repassaConsultasPaginadas() throws SQLException {
        // preparar totais do banco
        when(repositorio.contarFiltradosPorProfessor(4L, "a", "ativo")).thenReturn(3);
        when(repositorio.contarFiltradosParaAdmin("a", "todos")).thenReturn(8);

        // executar consultas
        conteudos.listarPorProfessorPaginado(4L, 1, "a", "ativo");
        conteudos.contarPorProfessor(4L);
        conteudos.listarTodosParaAdminPaginado(0, "a", "todos");
        int doProfessor = conteudos.contarFiltradosPorProfessor(4L, "a", "ativo");
        int doAdmin = conteudos.contarFiltradosParaAdmin("a", "todos");

        // verificar repasses
        assertEquals(3, doProfessor);
        assertEquals(8, doAdmin);
        verify(repositorio).listarPorProfessorPaginado(4L, 1, "a", "ativo");
        verify(repositorio).listarTodosParaAdminPaginado(eq(0), eq("a"), eq("todos"));
        assertTrue(conteudos.listarPorTurma(1L).isEmpty());
    }

    // montar material do teste este metodo cria um material da turma 1 com a duracao informada
    private ConteudoDTO conteudo(String duracao) {
        ConteudoDTO conteudo = new ConteudoDTO();
        conteudo.setIdTurma(1L);
        conteudo.setIdProfessor(4L);
        conteudo.setTitulo("Aula");
        conteudo.setDuracaoEstimada(duracao);
        return conteudo;
    }
}

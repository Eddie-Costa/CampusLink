// verificacao da tela meus conteudos confere filtros paginacao midias e as acoes do professor sobre seus materiais
package com.example.CampusLink.controller.Usuarios.Professor;

import com.example.CampusLink.dto.ConteudoDTO;
import com.example.CampusLink.repository.ProfessorRepository;
import com.example.CampusLink.repository.TurmaRepository;
import com.example.CampusLink.repository.UsuarioRepository;
import com.example.CampusLink.service.ConteudoService;
import com.example.CampusLink.service.DenunciaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// testes da tela meus conteudos esta classe simula o professor 4 com seus materiais
class MeusConteudosControllerTests {

    private static final String PROFESSOR = "prof@teste.com";

    private ConteudoService conteudos;
    private ProfessorRepository professores;
    private TurmaRepository turmas;
    private MeusConteudosController controlador;
    private ExtendedModelMap modelo;

    // preparar testes da tela este metodo cria o controlador com servicos simulados e o professor 4
    @BeforeEach
    void preparar() throws SQLException {
        conteudos = mock(ConteudoService.class);
        DenunciaService denuncias = mock(DenunciaService.class);
        professores = mock(ProfessorRepository.class);
        turmas = mock(TurmaRepository.class);
        controlador = new MeusConteudosController(conteudos, denuncias, professores);
        ReflectionTestUtils.setField(controlador, "UsuarioRepository", mock(UsuarioRepository.class));
        ReflectionTestUtils.setField(controlador, "turmaRepository", turmas);
        modelo = new ExtendedModelMap();
        when(professores.buscarPorIDProfessor(PROFESSOR)).thenReturn("4");
        when(professores.buscarPorIDProfessor("sem@teste.com")).thenReturn(" ");
        when(denuncias.listarNomesAlunos(anyLong())).thenReturn(List.of("Ana"));
        when(conteudos.contarPorProfessor(4L)).thenReturn(Map.of("totalConteudos", 12, "totalAtivos", 9, "totalSuspensos", 2, "totalRemovidos", 1));
    }

    // testar tela completa este teste confere filtros limpos totais tipo de exibicao e indicacao de mais paginas
    @Test
    @SuppressWarnings("unchecked")
    void exibeMateriaisComFiltrosEMidias() throws SQLException {
        // preparar dez materiais de onze filtrados com video livro arquivo e texto
        List<ConteudoDTO> pagina = new java.util.ArrayList<>(List.of(
                material(1L, "link", "https://www.youtube.com/watch?v=abcdefghijk"), material(2L, "link", "https://youtu.be/ABCDEFGHIJK"),
                material(3L, "link", "https://www.youtube.com/embed/zyxwvutsrqp"), material(4L, "link", "https://books.google.com/books?id=livro1"),
                material(5L, "link", "https://books.google.com/books/edition/_/abc_123"), material(6L, "arquivo", null),
                material(7L, "texto", " "), material(8L, null, "nao e url"), material(9L, "link", "https://site.com/x"),
                material(10L, "link", "https://youtube.com/watch?v=curto")));
        when(conteudos.listarPorProfessorPaginado(4L, 0, "aula", "ativo")).thenReturn(pagina);
        when(conteudos.contarFiltradosPorProfessor(4L, "aula", "ativo")).thenReturn(11);

        // executar tela com pagina negativa e filtros com espacos
        String tela = controlador.meusConteudos(sessao(PROFESSOR, "professor"), modelo, -3, "  aula ", " ATIVO ", false);

        // verificar tela filtros totais e midias
        assertEquals("Usuarios/Professor/meusConteudos", tela);
        assertEquals("ativo", modelo.get("statusSelecionado"));
        assertEquals("aula", modelo.get("busca"));
        assertEquals(true, modelo.get("temMais"));
        assertEquals(12, modelo.get("totalConteudos"));
        Map<Long, String> exibicao = (Map<Long, String>) modelo.get("tiposExibicaoPorConteudo");
        assertEquals("Vídeo", exibicao.get(1L));
        assertEquals("Livro", exibicao.get(5L));
        assertEquals("Arquivo", exibicao.get(6L));
        assertEquals("Texto", exibicao.get(7L));
        assertEquals("Link", exibicao.get(10L));
        assertEquals(List.of("Ana"), pagina.get(0).getAlunosDenunciaram());
    }

    // testar carregamento parcial este teste confere o bloco devolvido ao rolar a pagina e a situacao invalida trocada por todos
    @Test
    void devolveSoOBlocoDeMateriaisAoRolar() throws SQLException {
        // preparar pagina vazia

        // executar carregamento parcial
        String bloco = controlador.meusConteudos(sessao(PROFESSOR, "professor"), modelo, 2, "", "apagado", true);

        // verificar bloco e filtro
        assertEquals("Usuarios/Professor/meusConteudos :: blocoConteudos", bloco);
        assertEquals("todos", modelo.get("statusSelecionado"));
        assertEquals(3L, modelo.get("proximaPagina"));
        verify(conteudos, never()).contarPorProfessor(anyLong());
    }

    // testar falha na tela este teste confere volta ao inicio na tela completa e erro http no carregamento parcial
    @Test
    void trataFalhaAoCarregarMateriais() throws SQLException {
        // preparar banco indisponivel
        when(conteudos.listarPorProfessorPaginado(anyLong(), anyInt(), anyString(), anyString())).thenThrow(new SQLException("banco"));

        // executar tela completa e parcial
        String completa = controlador.meusConteudos(sessao(PROFESSOR, "professor"), modelo, 0, "", "todos", false);

        // verificar destino e erro http
        assertEquals("redirect:/home", completa);
        assertThrows(ResponseStatusException.class,
                () -> controlador.meusConteudos(sessao(PROFESSOR, "professor"), modelo, 0, "", "todos", true));
    }

    // testar acesso este teste confere visitante aluno sessao sem email e professor sem cadastro em todas as acoes
    @Test
    void bloqueiaQuemNaoEhProfessorCadastrado() {
        // preparar sessoes invalidas
        RedirectAttributesModelMap redirecionamento = new RedirectAttributesModelMap();
        List<Function<MockHttpSession, String>> acoes = List.of(
                s -> controlador.meusConteudos(s, modelo, 0, "", "todos", false),
                s -> controlador.removerConteudo(10L, s, redirecionamento),
                s -> controlador.solicitarRevisao(10L, s, redirecionamento),
                s -> controlador.reDisponibilizar(10L, s, redirecionamento));

        // executar cada acao e verificar o destino
        for (Function<MockHttpSession, String> acao : acoes) {
            assertEquals("redirect:/login", acao.apply(new MockHttpSession()));
            assertEquals("redirect:/home", acao.apply(sessao("aluno@teste.com", "aluno")));
            assertEquals("redirect:/login", acao.apply(sessao(null, "professor")));
            assertEquals("redirect:/home", acao.apply(sessao("sem@teste.com", "professor")));
        }
    }

    // testar acoes sobre o material este teste confere as mensagens de sucesso recusa e falha de cada acao
    @Test
    void executaAcoesDoProfessorComMensagens() {
        // preparar material 10 aceito material 20 recusado e material 30 com falha
        when(conteudos.removerConteudoProfessor(10L, 4L)).thenReturn(true);
        when(conteudos.solicitarRevisao(10L, 4L)).thenReturn(true);
        when(conteudos.reabrir(10L, 4L, null)).thenReturn(true);
        when(conteudos.removerConteudoProfessor(30L, 4L)).thenThrow(new RuntimeException("banco"));
        when(conteudos.solicitarRevisao(30L, 4L)).thenThrow(new RuntimeException("banco"));
        when(conteudos.reabrir(30L, 4L, null)).thenThrow(new RuntimeException("banco"));

        // executar acoes e verificar mensagens
        assertEquals("Conteúdo removido com sucesso.", mensagem(r -> controlador.removerConteudo(10L, sessao(PROFESSOR, "professor"), r), "mensagemSucesso"));
        assertEquals("Não foi possível remover o conteúdo.", mensagem(r -> controlador.removerConteudo(20L, sessao(PROFESSOR, "professor"), r), "mensagemErro"));
        assertEquals("Não foi possível remover o conteúdo.", mensagem(r -> controlador.removerConteudo(30L, sessao(PROFESSOR, "professor"), r), "mensagemErro"));
        assertEquals("Solicitação de análise enviada ao administrador.", mensagem(r -> controlador.solicitarRevisao(10L, sessao(PROFESSOR, "professor"), r), "mensagemSucesso"));
        assertEquals("Não foi possível solicitar a análise deste conteúdo.", mensagem(r -> controlador.solicitarRevisao(20L, sessao(PROFESSOR, "professor"), r), "mensagemErro"));
        assertEquals("Não foi possível solicitar a análise.", mensagem(r -> controlador.solicitarRevisao(30L, sessao(PROFESSOR, "professor"), r), "mensagemErro"));
        assertEquals("Conteúdo disponível novamente para os alunos concluírem.", mensagem(r -> controlador.reDisponibilizar(10L, sessao(PROFESSOR, "professor"), r), "mensagemSucesso"));
        assertEquals("Não foi possível re-disponibilizar este conteúdo.", mensagem(r -> controlador.reDisponibilizar(20L, sessao(PROFESSOR, "professor"), r), "mensagemErro"));
        assertEquals("Não foi possível re-disponibilizar o conteúdo.", mensagem(r -> controlador.reDisponibilizar(30L, sessao(PROFESSOR, "professor"), r), "mensagemErro"));
        verify(turmas).registrarAlteracaoPorConteudo(PROFESSOR, 10L);
        verify(turmas, never()).registrarAlteracaoPorConteudo(any(), org.mockito.ArgumentMatchers.eq(20L));
    }

    // obter mensagem este metodo executa a acao e devolve a mensagem guardada para a proxima tela
    private String mensagem(Function<RedirectAttributesModelMap, String> acao, String chave) {
        RedirectAttributesModelMap redirecionamento = new RedirectAttributesModelMap();
        assertEquals("redirect:/meus-conteudos", acao.apply(redirecionamento));
        return (String) redirecionamento.getFlashAttributes().get(chave);
    }

    // montar sessao este metodo cria a sessao logada com email e perfil
    private MockHttpSession sessao(String email, String tipo) {
        MockHttpSession sessao = new MockHttpSession();
        sessao.setAttribute("usuarioLogado", new Object());
        sessao.setAttribute("email2FA", email);
        sessao.setAttribute("tipoUsuario", tipo);
        return sessao;
    }

    // montar material este metodo cria o material com codigo tipo e link
    private ConteudoDTO material(Long id, String tipo, String url) {
        ConteudoDTO conteudo = new ConteudoDTO();
        conteudo.setId(id);
        conteudo.setTipo(tipo);
        conteudo.setUrl(url);
        return conteudo;
    }
}

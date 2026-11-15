// verificacao da denuncia pela tela confere que so aluno da turma denuncia material da propria turma e a mensagem de cada resultado
package com.example.CampusLink.controller.Geral;

import com.example.CampusLink.dto.TurmaDTO;
import com.example.CampusLink.repository.AlunoRepository;
import com.example.CampusLink.repository.ConteudoRepository;
import com.example.CampusLink.repository.TurmaRepository;
import com.example.CampusLink.repository.UsuarioRepository;
import com.example.CampusLink.service.DenunciaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// testes da denuncia pela tela esta classe simula o aluno 7 na turma 1 com o material 10
class DenunciaControllerTests {

    private static final String ALUNO = "aluno@teste.com";

    private DenunciaService denuncias;
    private AlunoRepository alunos;
    private ConteudoRepository conteudos;
    private DenunciaController controlador;
    private RedirectAttributesModelMap redirecionamento;

    // preparar testes da denuncia este metodo cria o controlador com o aluno 7 matriculado na turma 1
    @BeforeEach
    void preparar() throws SQLException {
        denuncias = mock(DenunciaService.class);
        alunos = mock(AlunoRepository.class);
        TurmaRepository turmas = mock(TurmaRepository.class);
        conteudos = mock(ConteudoRepository.class);
        controlador = new DenunciaController(denuncias, alunos, turmas, conteudos);
        ReflectionTestUtils.setField(controlador, "UsuarioRepository", mock(UsuarioRepository.class));
        redirecionamento = new RedirectAttributesModelMap();
        TurmaDTO turma = new TurmaDTO();
        turma.setId(1L);
        when(alunos.buscarPorIDAluno(ALUNO)).thenReturn("7");
        when(alunos.buscarPorIDAluno("sem@teste.com")).thenReturn("");
        when(alunos.buscarPorIDAluno("falha@teste.com")).thenThrow(new SQLException("banco"));
        when(turmas.buscarTurmasDoUsuario("aluno", "7")).thenReturn(List.of(turma));
        when(conteudos.conteudoPertenceTurma(10L, 1L)).thenReturn(true);
    }

    // testar resultado da denuncia este teste confere a mensagem mostrada para cada resposta do servico
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "sucesso|mensagemSucesso|Denúncia registrada com sucesso.",
            "suspenso|mensagemSucesso|Conteúdo suspenso para análise.",
            "duplicada|mensagemErro|Você já denunciou este conteúdo.",
            "motivo_vazio|mensagemErro|Informe o motivo da denúncia.",
            "conteudo_nao_encontrado|mensagemErro|Conteúdo não encontrado.",
            "conteudo_indisponivel|mensagemErro|Este conteúdo não está disponível para denúncia.",
            "professor_nao_encontrado|mensagemErro|Não foi possível identificar o professor responsável.",
            "turma_sem_alunos|mensagemErro|Não foi possível identificar os alunos da turma.",
            "erro|mensagemErro|Não foi possível registrar a denúncia."})
    void mostraMensagemDeCadaResultado(String resultado, String chave, String mensagem) {
        // preparar resposta do servico
        when(denuncias.registrarDenuncia(10L, 7L, 1L, "Ofensivo")).thenReturn(resultado);

        // executar denuncia
        String destino = controlador.denunciarConteudo(1L, 10L, "Ofensivo", sessao(ALUNO, "aluno"), redirecionamento);

        // verificar destino e mensagem
        assertEquals("redirect:/turmas/1", destino);
        assertEquals(mensagem, redirecionamento.getFlashAttributes().get(chave));
    }

    // testar denuncia invalida este teste confere visitante professor aluno sem cadastro turma alheia material de outra turma e falha
    @Test
    void recusaDenunciaForaDasRegras() {
        // preparar sessoes e pedidos invalidos

        // executar denuncias e verificar destinos e mensagens
        assertEquals("redirect:/login", controlador.denunciarConteudo(1L, 10L, "m", new MockHttpSession(), redirecionamento));
        assertEquals("Apenas alunos podem denunciar conteúdos.", erro(1L, 10L, sessao("prof@teste.com", "professor"), "redirect:/turmas/1"));
        assertEquals("redirect:/login", controlador.denunciarConteudo(1L, 10L, "m", sessao(" ", "aluno"), redirecionamento));
        assertEquals("Não foi possível identificar o aluno.", erro(1L, 10L, sessao("sem@teste.com", "aluno"), "redirect:/turmas/1"));
        assertEquals("Você não participa desta turma.", erro(2L, 10L, sessao(ALUNO, "aluno"), "redirect:/turmas"));
        assertEquals("Este conteúdo não pertence à turma.", erro(1L, 20L, sessao(ALUNO, "aluno"), "redirect:/turmas/1"));
        assertEquals("Não foi possível registrar a denúncia.", erro(1L, 10L, sessao("falha@teste.com", "aluno"), "redirect:/turmas/1"));
        verify(denuncias, never()).registrarDenuncia(anyLong(), anyLong(), anyLong(), any());
    }

    // tentar denuncia este metodo envia a denuncia confere o destino e devolve a mensagem de erro
    private String erro(Long idTurma, Long idConteudo, MockHttpSession sessao, String destinoEsperado) {
        RedirectAttributesModelMap mensagens = new RedirectAttributesModelMap();
        assertEquals(destinoEsperado, controlador.denunciarConteudo(idTurma, idConteudo, "motivo", sessao, mensagens));
        return (String) mensagens.getFlashAttributes().get("mensagemErro");
    }

    // montar sessao este metodo cria a sessao logada com email e perfil
    private MockHttpSession sessao(String email, String tipo) {
        MockHttpSession sessao = new MockHttpSession();
        sessao.setAttribute("usuarioLogado", new Object());
        sessao.setAttribute("email2FA", email);
        sessao.setAttribute("tipoUsuario", tipo);
        return sessao;
    }
}

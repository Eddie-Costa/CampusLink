// verificacao da gestao de turmas pelo administrador confere acesso cadastro edicao e entrada e saida de participantes
package com.example.CampusLink.controller.Usuarios.Admin;

import com.example.CampusLink.dto.Admin.cadastrarTurmaAdminDTO;
import com.example.CampusLink.dto.Admin.editarTurmaAdminDTO;
import com.example.CampusLink.dto.Admin.loginAdminDTO;
import com.example.CampusLink.repository.AdminTurmaRepository;
import com.example.CampusLink.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.sql.SQLException;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// testes da gestao de turmas esta classe simula a turma 1 do professor 4 e um administrador logado
class AdminTurmasControllerTests {

    private AdminTurmaRepository repositorio;
    private AdminTurmasController controlador;
    private MockHttpSession admin;
    private ExtendedModelMap modelo;
    private RedirectAttributesModelMap redirecionamento;

    // preparar testes da gestao este metodo cria o controlador com o banco simulado e a sessao do administrador
    @BeforeEach
    void preparar() throws SQLException {
        repositorio = mock(AdminTurmaRepository.class);
        controlador = new AdminTurmasController(repositorio);
        ReflectionTestUtils.setField(controlador, "UsuarioRepository", mock(UsuarioRepository.class));
        admin = sessaoAdmin("admin@teste.com", "Admin@Teste.com ");
        modelo = new ExtendedModelMap();
        redirecionamento = new RedirectAttributesModelMap();
        editarTurmaAdminDTO turma = new editarTurmaAdminDTO();
        turma.setId(1L);
        turma.setIdProprietario(4L);
        when(repositorio.buscarTurmaPorId(1L)).thenReturn(turma);
    }

    // testar acesso a gestao este teste confere que visitante vai ao login e outros perfis voltam ao inicio em todas as telas
    @Test
    void bloqueiaQuemNaoEhAdministrador() throws SQLException {
        // preparar visitante aluno e administrador com email diferente da sessao
        MockHttpSession visitante = new MockHttpSession();
        MockHttpSession aluno = new MockHttpSession();
        aluno.setAttribute("usuarioLogado", new Object());
        aluno.setAttribute("tipoUsuario", "aluno");
        MockHttpSession adminTrocado = sessaoAdmin("admin@teste.com", "outro@teste.com");
        MockHttpSession adminSemEmail = sessaoAdmin(" ", " ");
        List<Function<MockHttpSession, String>> telas = List.of(
                s -> controlador.listarTurmas(s, modelo),
                s -> controlador.exibirCadastroTurma(s, modelo),
                s -> controlador.cadastrarTurma(new cadastrarTurmaAdminDTO(), semErros(), s, modelo, redirecionamento),
                s -> controlador.exibirEdicaoTurma(1L, s, modelo, redirecionamento),
                s -> controlador.editarTurma(1L, new editarTurmaAdminDTO(), semErros(), s, modelo, redirecionamento),
                s -> controlador.listarAlunosDaTurma(1L, s, modelo, redirecionamento),
                s -> controlador.adicionarAlunoNaTurma(1L, 7L, s, redirecionamento),
                s -> controlador.removerAlunoDaTurma(1L, 7L, s, redirecionamento),
                s -> controlador.listarProfessoresDaTurma(1L, s, modelo, redirecionamento),
                s -> controlador.adicionarProfessorNaTurma(1L, 5L, s, redirecionamento),
                s -> controlador.removerProfessorDaTurma(1L, 5L, s, redirecionamento));

        // executar cada tela com os tres perfis e verificar o destino
        for (Function<MockHttpSession, String> tela : telas) {
            assertEquals("redirect:/admin/login", tela.apply(visitante));
            assertEquals("redirect:/home", tela.apply(aluno));
            assertEquals("redirect:/home", tela.apply(adminTrocado));
            assertEquals("redirect:/home", tela.apply(adminSemEmail));
        }
        verify(repositorio, never()).buscarTurmaPorId(anyLong());
    }

    // testar listagem e cadastro este teste confere a lista a tela de cadastro a gravacao e a tela com erros
    @Test
    void listaECadastraTurmas() throws SQLException {
        // preparar formulario valido e formulario com erro
        cadastrarTurmaAdminDTO nova = new cadastrarTurmaAdminDTO();
        BindingResult comErro = new BeanPropertyBindingResult(nova, "turma");
        comErro.reject("nome", "Informe o nome");

        // executar telas
        String lista = controlador.listarTurmas(admin, modelo);
        String cadastro = controlador.exibirCadastroTurma(admin, modelo);
        String gravada = controlador.cadastrarTurma(nova, semErros(), admin, modelo, redirecionamento);
        String invalida = controlador.cadastrarTurma(nova, comErro, admin, modelo, redirecionamento);

        // verificar destinos e gravacao
        assertEquals("Usuarios/Admin/turmasAdmin", lista);
        assertEquals("Usuarios/Admin/cadastrarTurmaAdmin", cadastro);
        assertEquals("redirect:/admin/turmas", gravada);
        assertEquals("Turma cadastrada com sucesso!", redirecionamento.getFlashAttributes().get("mensagemSucesso"));
        assertEquals("Usuarios/Admin/cadastrarTurmaAdmin", invalida);
        verify(repositorio).cadastrarTurma(nova);
    }

    // testar falhas na listagem e no cadastro este teste confere as mensagens quando o banco esta indisponivel
    @Test
    void avisaFalhasNaListagemENoCadastro() throws SQLException {
        // preparar banco indisponivel
        when(repositorio.listarTurmas()).thenThrow(new SQLException("banco"));
        when(repositorio.listarProfessoresAtivos()).thenThrow(new SQLException("banco"));
        when(repositorio.cadastrarTurma(any())).thenThrow(new SQLException("banco"));

        // executar telas
        ExtendedModelMap modeloLista = new ExtendedModelMap();
        controlador.listarTurmas(admin, modeloLista);
        ExtendedModelMap modeloCadastro = new ExtendedModelMap();
        controlador.exibirCadastroTurma(admin, modeloCadastro);
        String gravacao = controlador.cadastrarTurma(new cadastrarTurmaAdminDTO(), semErros(), admin, modelo, redirecionamento);

        // verificar mensagens e listas vazias
        assertEquals("Não foi possível carregar as turmas.", modeloLista.get("mensagemErro"));
        assertEquals("Não foi possível carregar os professores.", modeloCadastro.get("mensagemErro"));
        assertEquals("Usuarios/Admin/cadastrarTurmaAdmin", gravacao);
        assertEquals("Não foi possível cadastrar a turma.", modelo.get("mensagemErro"));
        assertTrue(((List<?>) modelo.get("professores")).isEmpty());
    }

    // testar edicao este teste confere tela de edicao gravacao recusa erro de validacao e turma inexistente
    @Test
    void editaTurmaExistente() throws SQLException {
        // preparar edicao aceita
        editarTurmaAdminDTO edicao = new editarTurmaAdminDTO();
        when(repositorio.atualizarTurma(edicao)).thenReturn(true);
        BindingResult comErro = new BeanPropertyBindingResult(edicao, "turma");
        comErro.reject("nome", "Informe o nome");

        // executar telas e edicoes
        String tela = controlador.exibirEdicaoTurma(1L, admin, modelo, redirecionamento);
        String gravada = controlador.editarTurma(1L, edicao, semErros(), admin, modelo, redirecionamento);
        String invalida = controlador.editarTurma(1L, edicao, comErro, admin, modelo, redirecionamento);
        String inexistente = controlador.exibirEdicaoTurma(9L, admin, modelo, redirecionamento);
        String gravacaoInexistente = controlador.editarTurma(9L, new editarTurmaAdminDTO(), semErros(), admin, modelo, redirecionamento);
        String recusada = controlador.editarTurma(1L, new editarTurmaAdminDTO(), semErros(), admin, modelo, redirecionamento);

        // verificar destinos codigo da turma e mensagens
        assertEquals("Usuarios/Admin/editarTurmaAdmin", tela);
        assertEquals("redirect:/admin/turmas", gravada);
        assertEquals(1L, edicao.getId());
        assertEquals("Usuarios/Admin/editarTurmaAdmin", invalida);
        assertEquals("redirect:/admin/turmas", inexistente);
        assertEquals("redirect:/admin/turmas", gravacaoInexistente);
        assertEquals("Usuarios/Admin/editarTurmaAdmin", recusada);
        assertEquals("Não foi possível atualizar a turma.", modelo.get("mensagemErro"));
    }

    // testar falha na edicao este teste confere as mensagens quando o banco falha ao abrir ou gravar a edicao
    @Test
    void avisaFalhasNaEdicao() throws SQLException {
        // preparar banco indisponivel na turma 2
        when(repositorio.buscarTurmaPorId(2L)).thenThrow(new SQLException("banco"));

        // executar telas
        String tela = controlador.exibirEdicaoTurma(2L, admin, modelo, redirecionamento);
        String gravacao = controlador.editarTurma(2L, new editarTurmaAdminDTO(), semErros(), admin, modelo, redirecionamento);

        // verificar mensagens
        assertEquals("redirect:/admin/turmas", tela);
        assertEquals("Não foi possível carregar a turma.", redirecionamento.getFlashAttributes().get("mensagemErro"));
        assertEquals("Usuarios/Admin/editarTurmaAdmin", gravacao);
        assertEquals("Não foi possível atualizar a turma.", modelo.get("mensagemErro"));
    }

    // testar alunos da turma este teste confere a tela entrada e saida aceitas e recusadas
    @Test
    void gerenciaAlunosDaTurma() throws SQLException {
        // preparar entrada e saida aceitas para o aluno 7
        when(repositorio.adicionarAlunoNaTurma(1L, 7L)).thenReturn(true);
        when(repositorio.removerAlunoDaTurma(1L, 7L)).thenReturn(true);

        // executar telas e acoes
        String tela = controlador.listarAlunosDaTurma(1L, admin, modelo, redirecionamento);
        String entrou = controlador.adicionarAlunoNaTurma(1L, 7L, admin, redirecionamento);
        String mensagemEntrada = (String) redirecionamento.getFlashAttributes().get("mensagemSucesso");
        String saiu = controlador.removerAlunoDaTurma(1L, 7L, admin, redirecionamento);
        String mensagemSaida = (String) redirecionamento.getFlashAttributes().get("mensagemSucesso");
        controlador.adicionarAlunoNaTurma(1L, 8L, admin, redirecionamento);
        String recusaEntrada = (String) redirecionamento.getFlashAttributes().get("mensagemErro");
        controlador.removerAlunoDaTurma(1L, 8L, admin, redirecionamento);
        String recusaSaida = (String) redirecionamento.getFlashAttributes().get("mensagemErro");

        // verificar destinos e mensagens
        assertEquals("Usuarios/Admin/alunosTurmaAdmin", tela);
        assertEquals("redirect:/admin/turmas/1/alunos", entrou);
        assertEquals("redirect:/admin/turmas/1/alunos", saiu);
        assertEquals("Aluno adicionado à turma com sucesso!", mensagemEntrada);
        assertEquals("Aluno removido da turma com sucesso!", mensagemSaida);
        assertEquals("Não foi possível adicionar o aluno à turma.", recusaEntrada);
        assertEquals("Não foi possível remover o aluno da turma.", recusaSaida);
    }

    // testar professores da turma este teste confere a tela entrada saida e a protecao do professor responsavel
    @Test
    void gerenciaProfessoresProtegendoResponsavel() throws SQLException {
        // preparar entrada e saida aceitas para o professor 5
        when(repositorio.adicionarProfessorNaTurma(1L, 5L)).thenReturn(true);
        when(repositorio.removerProfessorDaTurma(1L, 5L)).thenReturn(true);

        // executar telas e acoes
        String tela = controlador.listarProfessoresDaTurma(1L, admin, modelo, redirecionamento);
        controlador.adicionarProfessorNaTurma(1L, 5L, admin, redirecionamento);
        String mensagemEntrada = (String) redirecionamento.getFlashAttributes().get("mensagemSucesso");
        controlador.removerProfessorDaTurma(1L, 5L, admin, redirecionamento);
        String mensagemSaida = (String) redirecionamento.getFlashAttributes().get("mensagemSucesso");
        controlador.removerProfessorDaTurma(1L, 4L, admin, redirecionamento);
        String protecao = (String) redirecionamento.getFlashAttributes().get("mensagemErro");
        controlador.adicionarProfessorNaTurma(1L, 6L, admin, redirecionamento);
        String recusaEntrada = (String) redirecionamento.getFlashAttributes().get("mensagemErro");
        controlador.removerProfessorDaTurma(1L, 6L, admin, redirecionamento);
        String recusaSaida = (String) redirecionamento.getFlashAttributes().get("mensagemErro");

        // verificar destinos mensagens e que o responsavel nao foi removido
        assertEquals("Usuarios/Admin/professoresTurmaAdmin", tela);
        assertEquals("Professor adicionado à turma com sucesso!", mensagemEntrada);
        assertEquals("Professor removido da turma com sucesso!", mensagemSaida);
        assertTrue(protecao.contains("responsável pela turma não pode ser removido"));
        assertEquals("Não foi possível adicionar o professor à turma.", recusaEntrada);
        assertEquals("Não foi possível remover o professor da turma.", recusaSaida);
        verify(repositorio, never()).removerProfessorDaTurma(1L, 4L);
    }

    // testar turma inexistente e falhas este teste confere o retorno a lista e as mensagens de falha nos participantes
    @Test
    void trataTurmaInexistenteEFalhasNosParticipantes() throws SQLException {
        // preparar turma 2 com banco indisponivel
        when(repositorio.buscarTurmaPorId(2L)).thenThrow(new SQLException("banco"));
        List<Function<Long, String>> acoes = List.of(
                id -> controlador.listarAlunosDaTurma(id, admin, modelo, redirecionamento),
                id -> controlador.adicionarAlunoNaTurma(id, 7L, admin, redirecionamento),
                id -> controlador.removerAlunoDaTurma(id, 7L, admin, redirecionamento),
                id -> controlador.listarProfessoresDaTurma(id, admin, modelo, redirecionamento),
                id -> controlador.adicionarProfessorNaTurma(id, 5L, admin, redirecionamento),
                id -> controlador.removerProfessorDaTurma(id, 5L, admin, redirecionamento));

        // executar cada acao com turma inexistente e com falha e verificar os destinos
        for (Function<Long, String> acao : acoes) {
            assertEquals("redirect:/admin/turmas", acao.apply(9L));
            String comFalha = acao.apply(2L);
            assertTrue(comFalha.startsWith("redirect:/admin/turmas"), comFalha);
            assertTrue(redirecionamento.getFlashAttributes().get("mensagemErro").toString().startsWith("Não foi possível"));
        }
    }

    // montar sessao do administrador este metodo cria o login do administrador com o email confirmado
    private MockHttpSession sessaoAdmin(String emailLogin, String emailSessao) {
        loginAdminDTO login = new loginAdminDTO();
        login.setEmail(emailLogin);
        MockHttpSession sessao = new MockHttpSession();
        sessao.setAttribute("usuarioLogado", login);
        sessao.setAttribute("tipoUsuario", "admin");
        sessao.setAttribute("email2FA", emailSessao);
        return sessao;
    }

    // montar formulario sem erros este metodo cria o resultado de validacao vazio
    private BindingResult semErros() {
        return new BeanPropertyBindingResult(new Object(), "turma");
    }
}

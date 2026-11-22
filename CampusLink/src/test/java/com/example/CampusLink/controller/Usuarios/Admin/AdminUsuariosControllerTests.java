// verificacao da gestao de usuarios pelo administrador confere acesso cadastro edicao ativacao e exclusao de contas
package com.example.CampusLink.controller.Usuarios.Admin;

import com.example.CampusLink.dto.Admin.cadastrarUsuarioAdminDTO;
import com.example.CampusLink.dto.Admin.loginAdminDTO;
import com.example.CampusLink.dto.Admin.usuarioAdminDTO;
import com.example.CampusLink.repository.AdminUsuarioRepository;
import com.example.CampusLink.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.beanvalidation.SpringValidatorAdapter;
import jakarta.validation.Validation;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.sql.SQLException;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// testes da gestao de usuarios esta classe simula um administrador ativo um aluno e outro administrador
class AdminUsuariosControllerTests {

    private static final String EMAIL_ADMIN = "admin@teste.com";

    private AdminUsuarioRepository gestao;
    private UsuarioRepository usuarios;
    private AdminUsuariosController controlador;
    private MockHttpSession admin;
    private ExtendedModelMap modelo;
    private RedirectAttributesModelMap redirecionamento;

    // preparar testes da gestao este metodo cria o controlador o administrador ativo e o aluno 2
    @BeforeEach
    void preparar() throws SQLException {
        gestao = mock(AdminUsuarioRepository.class);
        usuarios = mock(UsuarioRepository.class);
        controlador = new AdminUsuariosController(gestao, usuarios);
        admin = sessao(EMAIL_ADMIN);
        modelo = new ExtendedModelMap();
        redirecionamento = new RedirectAttributesModelMap();
        when(gestao.buscarAdministradorPorEmail(EMAIL_ADMIN)).thenReturn(conta(1L, "Administrador", true));
        when(gestao.buscarUsuarioPorId(2L)).thenReturn(conta(2L, "Aluno", true));
        when(gestao.buscarUsuarioPorId(5L)).thenReturn(conta(5L, "Administrador", true));
    }

    // testar acesso a gestao este teste confere visitante perfil errado e administrador desativado em todas as telas
    @Test
    void bloqueiaQuemNaoEhAdministradorAtivo() throws SQLException {
        // preparar visitante aluno e administrador desativado
        MockHttpSession aluno = new MockHttpSession();
        aluno.setAttribute("usuarioLogado", new Object());
        aluno.setAttribute("tipoUsuario", "aluno");
        when(gestao.buscarAdministradorPorEmail("inativo@teste.com")).thenReturn(conta(3L, "Administrador", false));
        when(gestao.buscarAdministradorPorEmail("falha@teste.com")).thenThrow(new SQLException("banco"));
        List<Function<MockHttpSession, String>> telas = List.of(
                s -> controlador.listarUsuarios(null, null, 0, s, modelo),
                s -> controlador.cadastrarUsuario(s, modelo),
                s -> controlador.salvarUsuario(novo("Senha@123"), semErros(), s, modelo, redirecionamento),
                s -> controlador.editarUsuario(2L, s, modelo, redirecionamento),
                s -> controlador.salvarEdicaoUsuario(2L, edicao("Ana"), semErros(), s, modelo, redirecionamento),
                s -> controlador.desativarUsuario(2L, s, redirecionamento),
                s -> controlador.ativarUsuario(2L, s, redirecionamento),
                s -> controlador.excluirAdministrador(5L, s, redirecionamento));

        // executar cada tela e verificar o destino
        for (Function<MockHttpSession, String> tela : telas) {
            assertEquals("redirect:/admin/login", tela.apply(new MockHttpSession()));
            assertEquals("redirect:/home", tela.apply(aluno));
            assertEquals("redirect:/home", tela.apply(sessao("inativo@teste.com")));
            assertEquals("redirect:/home", tela.apply(sessao("falha@teste.com")));
        }
        verify(gestao, never()).atualizarStatusUsuario(anyLong(), anyBoolean(), anyString());
    }

    // testar lista de contas este teste confere a contagem de administradores ativos e a falha do banco
    @Test
    void listaContasContandoAdministradoresAtivos() throws SQLException {
        // preparar dois administradores ativos um inativo e um aluno
        when(gestao.listarUsuarios(null, null, 0)).thenReturn(new PageImpl<>(List.of(conta(1L, "Administrador", true),
                conta(5L, "Administrador", true), conta(6L, "Administrador", false), conta(2L, "Aluno", true))));
        when(gestao.totalAdministradoresAtivos()).thenReturn(2);

        // executar listagem e listagem com falha
        String tela = controlador.listarUsuarios(null, null, 0, admin, modelo);
        long ativos = ((Integer) modelo.get("totalAdministradoresAtivos"));
        when(gestao.listarUsuarios(null, null, 0)).thenThrow(new SQLException("banco"));
        ExtendedModelMap modeloFalha = new ExtendedModelMap();
        controlador.listarUsuarios(null, null, 0, admin, modeloFalha);

        // verificar contagem e mensagem
        assertEquals("Usuarios/Admin/usuariosAdmin", tela);
        assertEquals(2, ativos);
        assertEquals("Não foi possível carregar os usuários.", modeloFalha.get("mensagemErro"));
        assertEquals(0, modeloFalha.get("totalAdministradoresAtivos"));
    }

    // testar cadastro de conta este teste confere a senha criptografada e as recusas de senha data e dados repetidos
    @Test
    void cadastraContaComSenhaCriptografada() throws SQLException {
        // preparar dados validos e dados ja usados para o segundo cadastro
        when(usuarios.validarDadosDuplicados("Aluno", "123", "ana@teste.com")).thenReturn(List.of());
        cadastrarUsuarioAdminDTO senhaDiferente = novo("Senha@123");
        senhaDiferente.setConfirmarSenha("Outra@123");
        cadastrarUsuarioAdminDTO dataInvalida = novo("Senha@123");
        dataInvalida.setDataNasc("31/02/2000");
        cadastrarUsuarioAdminDTO repetido = novo("Senha@123");
        repetido.setEmail("repetido@teste.com");
        when(usuarios.validarDadosDuplicados("Aluno", "123", "repetido@teste.com")).thenReturn(List.of("Email já cadastrado"));
        BindingResult comErro = new BeanPropertyBindingResult(novo("x"), "usuario");
        comErro.reject("senha", "fraca");

        // executar cadastros
        String tela = controlador.cadastrarUsuario(admin, modelo);
        String cadastrado = controlador.salvarUsuario(novo("Senha@123"), semErros(), admin, modelo, redirecionamento);
        String senha = mensagem(m -> controlador.salvarUsuario(senhaDiferente, semErros(), admin, m, redirecionamento));
        String data = mensagem(m -> controlador.salvarUsuario(dataInvalida, semErros(), admin, m, redirecionamento));
        String duplicado = mensagem(m -> controlador.salvarUsuario(repetido, semErros(), admin, m, redirecionamento));
        String invalido = controlador.salvarUsuario(novo("x"), comErro, admin, modelo, redirecionamento);

        // verificar gravacao com senha criptografada e mensagens
        assertEquals("Usuarios/Admin/cadastrarUsuarioAdmin", tela);
        assertEquals("redirect:/admin/usuarios", cadastrado);
        verify(usuarios).InsertCadastroUsuarioIntoBD(eq("Aluno"), eq("123"), eq("Ana"), eq("ana@teste.com"),
                eq("2004-03-01"), startsWith("$2a$10$"));
        assertEquals("As senhas não são iguais.", senha);
        assertEquals("Informe uma data de nascimento válida.", data);
        assertEquals("Email já cadastrado.", duplicado);
        assertEquals("Usuarios/Admin/cadastrarUsuarioAdmin", invalido);
    }

    // testar falha no cadastro este teste confere a mensagem quando o banco nao grava a conta
    @Test
    void avisaFalhaAoGravarConta() throws SQLException {
        // preparar banco indisponivel
        when(usuarios.validarDadosDuplicados(any(), any(), any())).thenReturn(List.of());
        doThrow(new SQLException("banco")).when(usuarios).InsertCadastroUsuarioIntoBD(any(), any(), any(), any(), any(), any());

        // executar cadastro
        String mensagem = mensagem(m -> controlador.salvarUsuario(novo("Senha@123"), semErros(), admin, m, redirecionamento));

        // verificar mensagem
        assertEquals("Não foi possível cadastrar o usuário.", mensagem);
    }

    // testar edicao de conta este teste confere que administradores nao sao editados por esta tela e o aluno e atualizado
    @Test
    void editaApenasAlunosEProfessores() throws SQLException {
        // preparar edicao aceita do aluno
        when(gestao.validarDadosEdicao(eq(2L), eq("Aluno"), any(), any())).thenReturn(List.of());
        when(gestao.atualizarUsuario(any())).thenReturn(true);
        usuarioAdminDTO dados = edicao("Ana Maria");

        // executar telas e gravacoes
        String tela = controlador.editarUsuario(2L, admin, modelo, redirecionamento);
        String outroAdmin = controlador.editarUsuario(5L, admin, modelo, redirecionamento);
        String inexistente = controlador.editarUsuario(9L, admin, modelo, redirecionamento);
        String gravado = controlador.salvarEdicaoUsuario(2L, dados, semErros(), admin, modelo, redirecionamento);
        String gravacaoAdmin = controlador.salvarEdicaoUsuario(5L, edicao("X"), semErros(), admin, modelo, redirecionamento);
        String gravacaoInexistente = controlador.salvarEdicaoUsuario(9L, edicao("X"), semErros(), admin, modelo, redirecionamento);

        // verificar destinos e dados protegidos copiados da conta atual
        assertEquals("Usuarios/Admin/editarUsuarioAdmin", tela);
        assertEquals("redirect:/admin/usuarios", outroAdmin);
        assertEquals("redirect:/admin/usuarios", inexistente);
        assertEquals("redirect:/admin/usuarios", gravado);
        assertEquals("Aluno", dados.getPerfil());
        assertEquals(2L, dados.getIdUsuario());
        assertEquals("redirect:/admin/usuarios", gravacaoAdmin);
        assertEquals("redirect:/admin/usuarios", gravacaoInexistente);
        verify(gestao).atualizarUsuario(dados);
    }

    // testar busca e pagina este teste confere que texto email e pagina vao para o banco e a tela recebe a pagina
    @Test
    void buscaUsuarioPeloEmailCompleto() throws SQLException {
        // preparar a pagina devolvida pelo banco
        usuarioAdminDTO ana = conta(2L, "Aluno", true);
        ana.setEmail("ana@teste.com");
        when(gestao.listarUsuarios("ana", " ANA@teste.com ", 1)).thenReturn(new PageImpl<>(List.of(ana)));
        when(gestao.totalAdministradoresAtivos()).thenReturn(1);

        // executar busca com espacos e letras maiusculas
        controlador.listarUsuarios("ana", " ANA@teste.com ", 1, admin, modelo);

        // verificar a conta encontrada, o email repassado e a contagem de admins
        assertEquals(List.of(ana), modelo.get("usuarios"));
        assertEquals("ana", modelo.get("busca"));
        assertEquals("ANA@teste.com", modelo.get("buscaEmail"));
        assertEquals(1, modelo.get("totalAdministradoresAtivos"));
    }

    // testar edicao sem email este teste confere que o campo vazio mantem o email cadastrado, pois a tela so mostra a mascara
    @Test
    void edicaoComEmailVazioMantemEmailAtual() throws SQLException {
        // preparar aluno com email cadastrado e edicao sem email
        usuarioAdminDTO atual = conta(2L, "Aluno", true);
        atual.setEmail("ana.atual@teste.com");
        when(gestao.buscarUsuarioPorId(2L)).thenReturn(atual);
        when(gestao.validarDadosEdicao(eq(2L), eq("Aluno"), any(), eq("ana.atual@teste.com"))).thenReturn(List.of());
        when(gestao.atualizarUsuario(any())).thenReturn(true);
        usuarioAdminDTO dados = edicao("Ana Maria");
        dados.setEmail("");

        // executar gravacao validando o formulario como na tela
        String destino = controlador.salvarEdicaoUsuario(2L, dados, validar(dados), admin, modelo, redirecionamento);

        // verificar gravacao com o email atual
        assertEquals("redirect:/admin/usuarios", destino);
        assertEquals("ana.atual@teste.com", dados.getEmail());
        verify(gestao).atualizarUsuario(dados);
    }

    // testar campos da edicao este teste confere cada campo obrigatorio a data e os dados repetidos
    @Test
    void validaCamposDaEdicao() throws SQLException {
        // preparar edicoes com cada campo faltando
        usuarioAdminDTO semIdentificador = edicao("Ana");
        semIdentificador.setIdentificador(" ");
        usuarioAdminDTO semEmail = edicao("Ana");
        semEmail.setEmail("sem-arroba");
        usuarioAdminDTO semData = edicao("Ana");
        semData.setDataNasc(null);
        usuarioAdminDTO dataErrada = edicao("Ana");
        dataErrada.setDataNasc("2004-13-40");
        when(gestao.validarDadosEdicao(eq(2L), any(), any(), eq("repetido@teste.com"))).thenReturn(List.of("E-mail já cadastrado."));
        usuarioAdminDTO repetido = edicao("Ana");
        repetido.setEmail("repetido@teste.com");

        // executar edicoes e verificar mensagens
        // campos obrigatorios sao validados pelas anotacoes do dto, entao o teste usa o validador real
        assertCampoRecusado("nome", "Informe o nome do usuário.", edicao(" "));
        assertCampoRecusado("identificador", "Informe o RGM ou matrícula.", semIdentificador);
        assertCampoRecusado("email", "Informe um e-mail válido.", semEmail);
        assertCampoRecusado("dataNasc", "Informe a data de nascimento.", semData);
        assertEquals("Informe uma data de nascimento válida.", mensagem(m -> controlador.salvarEdicaoUsuario(2L, dataErrada, validar(dataErrada), admin, m, redirecionamento)));
        assertEquals("E-mail já cadastrado.", mensagem(m -> controlador.salvarEdicaoUsuario(2L, repetido, validar(repetido), admin, m, redirecionamento)));
        verify(gestao, never()).atualizarUsuario(any());
    }

    // testar falhas na edicao este teste confere gravacao recusada e falhas do banco ao abrir e gravar
    @Test
    void avisaFalhasNaEdicao() throws SQLException {
        // preparar gravacao recusada e banco indisponivel para a conta 7
        when(gestao.validarDadosEdicao(anyLong(), any(), any(), any())).thenReturn(List.of());
        when(gestao.atualizarUsuario(any())).thenReturn(false);
        when(gestao.buscarUsuarioPorId(7L)).thenThrow(new SQLException("banco"));

        // executar edicoes
        controlador.salvarEdicaoUsuario(2L, edicao("Ana"), semErros(), admin, modelo, redirecionamento);
        String recusada = (String) redirecionamento.getFlashAttributes().get("mensagemErro");
        controlador.editarUsuario(7L, admin, modelo, redirecionamento);
        String falhaAbrir = (String) redirecionamento.getFlashAttributes().get("mensagemErro");
        String falhaGravar = mensagem(m -> controlador.salvarEdicaoUsuario(7L, edicao("Ana"), semErros(), admin, m, redirecionamento));

        // verificar mensagens
        assertEquals("Não foi possível atualizar o usuário.", recusada);
        assertEquals("Não foi possível carregar o usuário.", falhaAbrir);
        assertEquals("Erro ao atualizar o usuário.", falhaGravar);
    }

    // testar situacao das contas este teste confere ativacao desativacao e as regras de administradores repassadas
    @Test
    void ativaEDesativaContasComRegrasDosAdministradores() throws SQLException {
        // preparar desativacao aceita do aluno ultimo administrador protegido e falha do banco
        when(gestao.atualizarStatusUsuario(2L, false, EMAIL_ADMIN)).thenReturn(true);
        when(gestao.atualizarStatusUsuario(2L, true, EMAIL_ADMIN)).thenReturn(true);
        when(gestao.atualizarStatusUsuario(5L, false, EMAIL_ADMIN)).thenThrow(new IllegalStateException("O sistema precisa manter pelo menos um administrador ativo."));
        when(gestao.atualizarStatusUsuario(5L, true, EMAIL_ADMIN)).thenThrow(new IllegalStateException("Somente outro administrador master pode alterar esta conta."));
        when(gestao.atualizarStatusUsuario(6L, false, EMAIL_ADMIN)).thenThrow(new SQLException("banco"));
        when(gestao.atualizarStatusUsuario(6L, true, EMAIL_ADMIN)).thenThrow(new SQLException("banco"));

        // executar alteracoes e guardar cada mensagem
        List<String> mensagens = List.of(
                acao(() -> controlador.desativarUsuario(2L, admin, redirecionamento), "mensagemSucesso"),
                acao(() -> controlador.ativarUsuario(2L, admin, redirecionamento), "mensagemSucesso"),
                acao(() -> controlador.desativarUsuario(9L, admin, redirecionamento), "mensagemErro"),
                acao(() -> controlador.ativarUsuario(9L, admin, redirecionamento), "mensagemErro"),
                acao(() -> controlador.desativarUsuario(5L, admin, redirecionamento), "mensagemErro"),
                acao(() -> controlador.ativarUsuario(5L, admin, redirecionamento), "mensagemErro"),
                acao(() -> controlador.desativarUsuario(6L, admin, redirecionamento), "mensagemErro"),
                acao(() -> controlador.ativarUsuario(6L, admin, redirecionamento), "mensagemErro"));

        // verificar mensagens na ordem das acoes
        assertEquals(List.of("Usuário desativado com sucesso.", "Usuário ativado com sucesso.",
                "Não foi possível desativar o usuário.", "Não foi possível ativar o usuário.",
                "O sistema precisa manter pelo menos um administrador ativo.", "Somente outro administrador master pode alterar esta conta.",
                "Erro ao desativar o usuário.", "Erro ao ativar o usuário."), mensagens);
    }

    // testar exclusao de administrador este teste confere exclusao aceita conta inexistente regra de protecao e falha do banco
    @Test
    void excluiAdministradorComRegras() throws SQLException {
        // preparar respostas para cada administrador
        when(gestao.excluirAdministrador(5L, EMAIL_ADMIN)).thenReturn(true);
        when(gestao.excluirAdministrador(1L, EMAIL_ADMIN)).thenThrow(new IllegalStateException("Você não pode excluir ou desativar sua própria conta de administrador."));
        when(gestao.excluirAdministrador(6L, EMAIL_ADMIN)).thenThrow(new SQLException("banco"));

        // executar exclusoes e verificar mensagens
        assertEquals("Administrador excluído com sucesso.", acao(() -> controlador.excluirAdministrador(5L, admin, redirecionamento), "mensagemSucesso"));
        assertEquals("Administrador não encontrado.", acao(() -> controlador.excluirAdministrador(9L, admin, redirecionamento), "mensagemErro"));
        assertTrue(acao(() -> controlador.excluirAdministrador(1L, admin, redirecionamento), "mensagemErro").contains("sua própria conta"));
        assertTrue(acao(() -> controlador.excluirAdministrador(6L, admin, redirecionamento), "mensagemErro").contains("vinculada a outros registros"));
    }

    // executar acao este metodo roda a acao com mensagens novas e devolve a mensagem pedida
    private String acao(java.util.function.Supplier<String> acao, String chave) {
        redirecionamento = new RedirectAttributesModelMap();
        String destino = acao.get();
        assertEquals("redirect:/admin/usuarios", destino);
        return (String) redirecionamento.getFlashAttributes().get(chave);
    }

    // obter mensagem da tela este metodo executa a acao com um modelo novo e devolve a mensagem de erro
    private String mensagem(Function<ExtendedModelMap, String> acao) {
        ExtendedModelMap novoModelo = new ExtendedModelMap();
        acao.apply(novoModelo);
        return (String) novoModelo.get("mensagemErro");
    }

    // montar sessao este metodo cria o login de administrador com o mesmo email confirmado
    private MockHttpSession sessao(String email) {
        loginAdminDTO login = new loginAdminDTO();
        login.setEmail(email);
        MockHttpSession sessao = new MockHttpSession();
        sessao.setAttribute("usuarioLogado", login);
        sessao.setAttribute("tipoUsuario", "admin");
        sessao.setAttribute("email2FA", email);
        return sessao;
    }

    // montar conta este metodo cria uma conta com codigo perfil e situacao
    private usuarioAdminDTO conta(Long id, String perfil, boolean ativa) {
        usuarioAdminDTO conta = new usuarioAdminDTO();
        conta.setIdUsuario(id);
        conta.setIdPerfil(id + 10);
        conta.setPerfil(perfil);
        conta.setStatus(ativa);
        return conta;
    }

    // montar novo cadastro este metodo cria o formulario de um aluno com a senha informada
    private cadastrarUsuarioAdminDTO novo(String senha) {
        cadastrarUsuarioAdminDTO usuario = new cadastrarUsuarioAdminDTO();
        usuario.setPerfil("Aluno");
        usuario.setIdentificador("123");
        usuario.setNome("Ana");
        usuario.setEmail("ana@teste.com");
        usuario.setDataNasc("2004-03-01");
        usuario.setSenha(senha);
        usuario.setConfirmarSenha(senha);
        return usuario;
    }

    // montar edicao este metodo cria os dados enviados pela tela de edicao
    private usuarioAdminDTO edicao(String nome) {
        usuarioAdminDTO usuario = new usuarioAdminDTO();
        usuario.setNome(nome);
        usuario.setIdentificador("12345678901");
        usuario.setEmail("ana@teste.com");
        usuario.setDataNasc("2004-03-01");
        usuario.setPerfil("Administrador");
        return usuario;
    }

    // validar formulario este metodo aplica as anotacoes do dto como o spring faz antes de chamar o controller
    private BindingResult validar(usuarioAdminDTO usuario) {
        BindingResult resultado = new BeanPropertyBindingResult(usuario, "usuario");
        new SpringValidatorAdapter(Validation.buildDefaultValidatorFactory().getValidator()).validate(usuario, resultado);
        return resultado;
    }

    // conferir campo recusado este metodo exige a mensagem esperada no campo e que a tela mostre um erro desse campo
    private void assertCampoRecusado(String campo, String esperada, usuarioAdminDTO usuario) {
        BindingResult resultado = validar(usuario);
        List<String> doCampo = resultado.getFieldErrors(campo).stream().map(erro -> erro.getDefaultMessage()).toList();
        assertTrue(doCampo.contains(esperada), campo + ": " + doCampo);
        assertTrue(doCampo.contains(mensagem(m -> controlador.salvarEdicaoUsuario(2L, usuario, resultado, admin, m, redirecionamento))));
    }

    // montar formulario sem erros este metodo cria o resultado de validacao vazio
    private BindingResult semErros() {
        return new BeanPropertyBindingResult(new Object(), "usuario");
    }
}

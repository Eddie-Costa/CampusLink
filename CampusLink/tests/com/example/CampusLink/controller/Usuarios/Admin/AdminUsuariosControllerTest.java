package com.example.CampusLink.controller.Usuarios.Admin;

import com.example.CampusLink.dao.*;
import com.example.CampusLink.dto.Admin.*;
import com.example.CampusLink.support.AdminFixture;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminUsuariosControllerTest {
    @Mock AdminUsuarioDAO dao;
    @Mock usuarioDAO usuarios;
    AdminUsuariosController controller;
    MockHttpSession session;
    ExtendedModelMap model;
    RedirectAttributesModelMap flash;
    @BeforeEach void preparar() {
        controller = new AdminUsuariosController(dao, usuarios);
        session = AdminFixture.sessao(); model = new ExtendedModelMap(); flash = new RedirectAttributesModelMap();
    }
    private cadastrarUsuarioAdminDTO cadastro() {
        var dto = new cadastrarUsuarioAdminDTO();
        dto.setPerfil("Aluno"); dto.setIdentificador("12345678901"); dto.setNome("Ana");
        dto.setEmail("ana@example.com"); dto.setTelefone("11999990000"); dto.setDataNasc("2000-01-01");
        dto.setSenha("SenhaTeste1!"); dto.setConfirmarSenha("SenhaTeste1!"); return dto;
    }
    private usuarioAdminDTO edicao() {
        var dto = new usuarioAdminDTO(); dto.setIdUsuario(1L); dto.setIdPerfil(2L); dto.setPerfil("Aluno");
        dto.setNome("Ana"); dto.setIdentificador("12345678901"); dto.setEmail("ana@example.com");
        dto.setTelefone("11999990000"); dto.setDataNasc("2000-01-01"); dto.setStatus(true); return dto;
    }
    private String salvar(cadastrarUsuarioAdminDTO dto) {
        return controller.salvarUsuario(dto, new BeanPropertyBindingResult(dto, "usuario"), session, model, flash);
    }
    @Test void deveListarUsuarios() throws Exception {
        var lista = List.of(edicao()); when(dao.listarUsuarios()).thenReturn(lista);
        assertEquals("Usuarios/Admin/usuariosAdmin", controller.listarUsuarios(session, model));
        assertSame(lista, model.get("usuarios"));
    }
    @Test void deveTratarErroAoListar() throws Exception {
        when(dao.listarUsuarios()).thenThrow(new SQLException("falha simulada"));
        assertEquals("Usuarios/Admin/usuariosAdmin", controller.listarUsuarios(session, model));
        assertEquals(List.of(), model.get("usuarios")); assertNotNull(model.get("mensagemErro"));
    }
    @Test void deveAbrirFormularioDeCadastro() {
        assertEquals("Usuarios/Admin/cadastrarUsuarioAdmin", controller.cadastrarUsuario(session, model));
        assertInstanceOf(cadastrarUsuarioAdminDTO.class, model.get("usuario"));
    }
    @Test void deveCadastrarUsuarioComSenhaCriptografada() throws Exception {
        var dto = cadastro();
        assertEquals("redirect:/admin/usuarios", salvar(dto));
        var senha = ArgumentCaptor.forClass(String.class);
        verify(usuarios).InsertCadastroUsuarioIntoBD(eq("Aluno"), eq(dto.getIdentificador()), eq("Ana"),
                eq(dto.getEmail()), eq(dto.getTelefone()), eq(dto.getDataNasc()), senha.capture());
        assertTrue(new BCryptPasswordEncoder().matches(dto.getSenha(), senha.getValue()));
        assertNotEquals(dto.getSenha(), senha.getValue()); assertNotNull(flash.getFlashAttributes().get("mensagemSucesso"));
    }
    @ParameterizedTest @ValueSource(strings = {"senha", "data", "duplicado", "validacao"})
    void deveRecusarCadastroInvalidoSemInserir(String caso) throws Exception {
        var dto = cadastro(); var result = new BeanPropertyBindingResult(dto, "usuario");
        switch (caso) {
            case "senha" -> dto.setConfirmarSenha("OutraSenha1!");
            case "data" -> dto.setDataNasc("data-invalida");
            case "duplicado" -> when(usuarios.validarDadosDuplicados(any(), any(), any(), any())).thenReturn(List.of("E-mail duplicado"));
            default -> result.reject("invalido");
        }
        assertEquals("Usuarios/Admin/cadastrarUsuarioAdmin", controller.salvarUsuario(dto, result, session, model, flash));
        verify(usuarios, never()).InsertCadastroUsuarioIntoBD(any(), any(), any(), any(), any(), any(), any());
        if (!caso.equals("validacao")) assertNotNull(model.get("mensagemErro"));
    }
    @Test void deveCarregarUsuarioParaEdicao() throws Exception {
        var dto = edicao(); when(dao.buscarUsuarioPorId(1L)).thenReturn(dto);
        assertEquals("Usuarios/Admin/editarUsuarioAdmin", controller.editarUsuario(1L, session, model, flash));
        assertSame(dto, model.get("usuario"));
    }
    @Test void deveTratarUsuarioInexistente() throws Exception {
        assertEquals("redirect:/admin/usuarios", controller.editarUsuario(99L, session, model, flash));
        assertEquals("Usuário não encontrado.", flash.getFlashAttributes().get("mensagemErro"));
    }
    @Test void deveEditarPreservandoIdentidadePerfilEStatus() throws Exception {
        var atual = edicao(); var alterado = edicao();
        alterado.setIdUsuario(999L); alterado.setIdPerfil(999L); alterado.setPerfil("admin"); alterado.setStatus(false);
        when(dao.buscarUsuarioPorId(1L)).thenReturn(atual); when(dao.atualizarUsuario(alterado)).thenReturn(true);
        assertEquals("redirect:/admin/usuarios", controller.salvarEdicaoUsuario(1L, alterado, session, model, flash));
        verify(dao).atualizarUsuario(alterado);
        assertAll(() -> assertEquals(1L, alterado.getIdUsuario()), () -> assertEquals(2L, alterado.getIdPerfil()),
                () -> assertEquals("Aluno", alterado.getPerfil()), () -> assertTrue(alterado.isStatus()));
    }
    @ParameterizedTest @ValueSource(strings = {"nome", "data", "duplicado"})
    void deveRecusarEdicaoInvalida(String caso) throws Exception {
        var dto = edicao(); when(dao.buscarUsuarioPorId(1L)).thenReturn(edicao());
        if (caso.equals("nome")) dto.setNome("  ");
        else if (caso.equals("data")) dto.setDataNasc("invalida");
        else when(dao.validarDadosEdicao(anyLong(), any(), any(), any(), any())).thenReturn(List.of("Duplicado"));
        assertEquals("Usuarios/Admin/editarUsuarioAdmin", controller.salvarEdicaoUsuario(1L, dto, session, model, flash));
        assertNotNull(model.get("mensagemErro")); verify(dao, never()).atualizarUsuario(any());
    }
    @ParameterizedTest @CsvSource({"true,true", "false,true", "true,false", "false,false"})
    void deveAtivarOuDesativarConformeResultadoDoDao(boolean ativar, boolean sucesso) throws Exception {
        when(dao.atualizarStatusUsuario(1L, ativar)).thenReturn(sucesso);
        String destino = ativar ? controller.ativarUsuario(1L, session, flash) : controller.desativarUsuario(1L, session, flash);
        assertEquals("redirect:/admin/usuarios", destino);
        assertNotNull(flash.getFlashAttributes().get(sucesso ? "mensagemSucesso" : "mensagemErro"));
        verify(dao).atualizarStatusUsuario(1L, ativar);
    }
    @ParameterizedTest @ValueSource(booleans = {true, false})
    void deveTratarErroAoAlterarStatus(boolean ativar) throws Exception {
        when(dao.atualizarStatusUsuario(1L, ativar)).thenThrow(new SQLException("falha simulada"));
        assertEquals("redirect:/admin/usuarios", ativar ? controller.ativarUsuario(1L, session, flash) : controller.desativarUsuario(1L, session, flash));
        assertNotNull(flash.getFlashAttributes().get("mensagemErro"));
    }
    static Stream<Arguments> acessos() {
        return Stream.of("listar", "formulario", "cadastrar", "editarForm", "editar", "ativar", "desativar")
                .flatMap(acao -> Stream.of("semSessao", "aluno", "emailDivergente").map(perfil -> Arguments.of(acao, perfil)));
    }
    @ParameterizedTest @MethodSource("acessos")
    void deveBloquearTodasOperacoesSemAdministradorValido(String acao, String perfil) throws Exception {
        if (perfil.equals("semSessao")) session = new MockHttpSession();
        else if (perfil.equals("aluno")) session.setAttribute("tipoUsuario", "aluno");
        else session.setAttribute("email2FA", "outro@example.com");
        String retorno = switch (acao) {
            case "listar" -> controller.listarUsuarios(session, model);
            case "formulario" -> controller.cadastrarUsuario(session, model);
            case "cadastrar" -> salvar(cadastro());
            case "editarForm" -> controller.editarUsuario(1L, session, model, flash);
            case "editar" -> controller.salvarEdicaoUsuario(1L, edicao(), session, model, flash);
            case "ativar" -> controller.ativarUsuario(1L, session, flash);
            default -> controller.desativarUsuario(1L, session, flash);
        };
        assertEquals(perfil.equals("semSessao") ? AdminFixture.LOGIN : "redirect:/home", retorno);
        verifyNoInteractions(dao, usuarios);
    }
}

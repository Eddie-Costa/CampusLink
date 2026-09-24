package com.example.CampusLink.controller.Usuarios.Admin;

import com.example.CampusLink.dao.AdminUsuarioDAO;
import com.example.CampusLink.dao.usuarioDAO;
import com.example.CampusLink.dto.Admin.cadastrarUsuarioAdminDTO;
import com.example.CampusLink.dto.Admin.loginAdminDTO;
import com.example.CampusLink.dto.Admin.usuarioAdminDTO;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Controller
public class AdminUsuariosController {

    private static final Logger logger = LoggerFactory.getLogger(AdminUsuariosController.class);

    private static final String URL_LOGIN_ADMIN = "/gestao/8f3c1d7a-2b94-4e61-a5c8-7d2f9b4a6e31";

    private final AdminUsuarioDAO adminUsuarioDAO;
    private final usuarioDAO usuarioDAO;

    public AdminUsuariosController(AdminUsuarioDAO adminUsuarioDAO, usuarioDAO usuarioDAO) {
        this.adminUsuarioDAO = adminUsuarioDAO;
        this.usuarioDAO = usuarioDAO;
    }

    @GetMapping("/admin/usuarios")
    public String listarUsuarios(HttpSession session, Model model) {

        // verifica se existe um usuario logado
        if (session.getAttribute("usuarioLogado") == null) {
            return "redirect:" + URL_LOGIN_ADMIN;
        }

        // permite somente o administrador
        if (!ehAdministrador(session)) {
            return "redirect:/home";
        }

        try {
            List<usuarioAdminDTO> usuarios = adminUsuarioDAO.listarUsuarios();
            model.addAttribute("usuarios", usuarios);

        } catch (SQLException e) {
            logger.error("erro ao carregar usuarios para o administrador", e);

            model.addAttribute("mensagemErro", "Não foi possível carregar os usuários.");
            model.addAttribute("usuarios", new ArrayList<usuarioAdminDTO>());
        }

        return "Usuarios/Admin/usuariosAdmin";
    }

    @GetMapping("/admin/usuarios/cadastrar")
    public String cadastrarUsuario(HttpSession session, Model model) {

        if (session.getAttribute("usuarioLogado") == null) {
            return "redirect:" + URL_LOGIN_ADMIN;
        }

        if (!ehAdministrador(session)) {
            return "redirect:/home";
        }

        model.addAttribute("usuario", new cadastrarUsuarioAdminDTO());

        return "Usuarios/Admin/cadastrarUsuarioAdmin";
    }

    @PostMapping("/admin/usuarios/cadastrar")
    public String salvarUsuario(
            @Valid @ModelAttribute("usuario") cadastrarUsuarioAdminDTO usuario,
            BindingResult result,
            HttpSession session,
            Model model,
            RedirectAttributes redirectAttributes
    ) {

        if (session.getAttribute("usuarioLogado") == null) {
            return "redirect:" + URL_LOGIN_ADMIN;
        }

        if (!ehAdministrador(session)) {
            return "redirect:/home";
        }

        if (result.hasErrors()) {
            return "Usuarios/Admin/cadastrarUsuarioAdmin";
        }

        if (!usuario.getSenha().equals(usuario.getConfirmarSenha())) {
            model.addAttribute("mensagemErro", "As senhas não são iguais.");
            return "Usuarios/Admin/cadastrarUsuarioAdmin";
        }

        try {
            LocalDate.parse(usuario.getDataNasc());

        } catch (DateTimeParseException e) {
            model.addAttribute("mensagemErro", "Informe uma data de nascimento válida.");
            return "Usuarios/Admin/cadastrarUsuarioAdmin";
        }

        try {
            List<String> erros = usuarioDAO.validarDadosDuplicados(
                    usuario.getPerfil(),
                    usuario.getIdentificador(),
                    usuario.getEmail(),
                    usuario.getTelefone()
            );

            if (!erros.isEmpty()) {
                model.addAttribute("mensagemErro", String.join(". ", erros) + ".");
                return "Usuarios/Admin/cadastrarUsuarioAdmin";
            }

            BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);
            String senhaCriptografada = encoder.encode(usuario.getSenha());

            usuarioDAO.InsertCadastroUsuarioIntoBD(
                    usuario.getPerfil(),
                    usuario.getIdentificador(),
                    usuario.getNome(),
                    usuario.getEmail(),
                    usuario.getTelefone(),
                    usuario.getDataNasc(),
                    senhaCriptografada
            );

            redirectAttributes.addFlashAttribute("mensagemSucesso", "Usuário cadastrado com sucesso.");

            return "redirect:/admin/usuarios";

        } catch (SQLException e) {
            logger.error("erro ao cadastrar usuario pelo administrador", e);

            model.addAttribute("mensagemErro", "Não foi possível cadastrar o usuário.");

            return "Usuarios/Admin/cadastrarUsuarioAdmin";
        }
    }

    @GetMapping("/admin/usuarios/{idUsuario}/editar")
    public String editarUsuario(
            @PathVariable Long idUsuario,
            HttpSession session,
            Model model,
            RedirectAttributes redirectAttributes
    ) {

        if (session.getAttribute("usuarioLogado") == null) {
            return "redirect:" + URL_LOGIN_ADMIN;
        }

        if (!ehAdministrador(session)) {
            return "redirect:/home";
        }

        try {
            usuarioAdminDTO usuario = adminUsuarioDAO.buscarUsuarioPorId(idUsuario);

            if (usuario == null) {
                redirectAttributes.addFlashAttribute("mensagemErro", "Usuário não encontrado.");
                return "redirect:/admin/usuarios";
            }

            model.addAttribute("usuario", usuario);

            return "Usuarios/Admin/editarUsuarioAdmin";

        } catch (SQLException e) {
            logger.error("erro ao buscar usuario {} para edicao", idUsuario, e);

            redirectAttributes.addFlashAttribute("mensagemErro", "Não foi possível carregar o usuário.");
            return "redirect:/admin/usuarios";
        }
    }

    @PostMapping("/admin/usuarios/{idUsuario}/editar")
    public String salvarEdicaoUsuario(
            @PathVariable Long idUsuario,
            @ModelAttribute("usuario") usuarioAdminDTO usuario,
            HttpSession session,
            Model model,
            RedirectAttributes redirectAttributes
    ) {

        if (session.getAttribute("usuarioLogado") == null) {
            return "redirect:" + URL_LOGIN_ADMIN;
        }

        if (!ehAdministrador(session)) {
            return "redirect:/home";
        }

        try {
            usuarioAdminDTO usuarioAtual = adminUsuarioDAO.buscarUsuarioPorId(idUsuario);

            if (usuarioAtual == null) {
                redirectAttributes.addFlashAttribute("mensagemErro", "Usuário não encontrado.");
                return "redirect:/admin/usuarios";
            }

            // mantem o id e o perfil que ja existem no banco
            usuario.setIdUsuario(idUsuario);
            usuario.setIdPerfil(usuarioAtual.getIdPerfil());
            usuario.setPerfil(usuarioAtual.getPerfil());
            usuario.setStatus(usuarioAtual.isStatus());

            String erroCampos = validarCampos(usuario);

            if (erroCampos != null) {
                model.addAttribute("mensagemErro", erroCampos);
                model.addAttribute("usuario", usuario);

                return "Usuarios/Admin/editarUsuarioAdmin";
            }

            List<String> erros = adminUsuarioDAO.validarDadosEdicao(
                    idUsuario,
                    usuario.getPerfil(),
                    usuario.getIdentificador(),
                    usuario.getEmail(),
                    usuario.getTelefone()
            );

            if (!erros.isEmpty()) {
                model.addAttribute("mensagemErro", String.join(" ", erros));
                model.addAttribute("usuario", usuario);

                return "Usuarios/Admin/editarUsuarioAdmin";
            }

            boolean atualizado = adminUsuarioDAO.atualizarUsuario(usuario);

            if (atualizado) {
                redirectAttributes.addFlashAttribute("mensagemSucesso", "Usuário atualizado com sucesso.");
            } else {
                redirectAttributes.addFlashAttribute("mensagemErro", "Não foi possível atualizar o usuário.");
            }

            return "redirect:/admin/usuarios";

        } catch (SQLException e) {
            logger.error("erro ao atualizar usuario {}", idUsuario, e);

            model.addAttribute("mensagemErro", "Erro ao atualizar o usuário.");
            model.addAttribute("usuario", usuario);

            return "Usuarios/Admin/editarUsuarioAdmin";
        }
    }

    @PostMapping("/admin/usuarios/{idUsuario}/desativar")
    public String desativarUsuario(
            @PathVariable Long idUsuario,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {

        if (session.getAttribute("usuarioLogado") == null) {
            return "redirect:" + URL_LOGIN_ADMIN;
        }

        if (!ehAdministrador(session)) {
            return "redirect:/home";
        }

        try {
            boolean alterado = adminUsuarioDAO.atualizarStatusUsuario(idUsuario, false);

            if (alterado) {
                redirectAttributes.addFlashAttribute("mensagemSucesso", "Usuário desativado com sucesso.");
            } else {
                redirectAttributes.addFlashAttribute("mensagemErro", "Não foi possível desativar o usuário.");
            }

        } catch (SQLException e) {
            logger.error("erro ao desativar usuario {}", idUsuario, e);

            redirectAttributes.addFlashAttribute("mensagemErro", "Erro ao desativar o usuário.");
        }

        return "redirect:/admin/usuarios";
    }

    @PostMapping("/admin/usuarios/{idUsuario}/ativar")
    public String ativarUsuario(
            @PathVariable Long idUsuario,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {

        if (session.getAttribute("usuarioLogado") == null) {
            return "redirect:" + URL_LOGIN_ADMIN;
        }

        if (!ehAdministrador(session)) {
            return "redirect:/home";
        }

        try {
            boolean alterado = adminUsuarioDAO.atualizarStatusUsuario(idUsuario, true);

            if (alterado) {
                redirectAttributes.addFlashAttribute("mensagemSucesso", "Usuário ativado com sucesso.");
            } else {
                redirectAttributes.addFlashAttribute("mensagemErro", "Não foi possível ativar o usuário.");
            }

        } catch (SQLException e) {
            logger.error("erro ao ativar usuario {}", idUsuario, e);

            redirectAttributes.addFlashAttribute("mensagemErro", "Erro ao ativar o usuário.");
        }

        return "redirect:/admin/usuarios";
    }

    private String validarCampos(usuarioAdminDTO usuario) {

        if (usuario.getNome() == null || usuario.getNome().isBlank()) {
            return "Informe o nome do usuário.";
        }

        if (usuario.getIdentificador() == null || usuario.getIdentificador().isBlank()) {
            return "Informe o RGM ou matrícula.";
        }

        if (usuario.getEmail() == null || usuario.getEmail().isBlank()) {
            return "Informe o e-mail.";
        }

        if (usuario.getTelefone() == null || usuario.getTelefone().isBlank()) {
            return "Informe o telefone.";
        }

        if (usuario.getDataNasc() == null || usuario.getDataNasc().isBlank()) {
            return "Informe a data de nascimento.";
        }

        try {
            LocalDate.parse(usuario.getDataNasc());
        } catch (DateTimeParseException e) {
            return "Informe uma data de nascimento válida.";
        }

        return null;
    }

    private boolean ehAdministrador(HttpSession session) {

        Object tipoUsuario = session.getAttribute("tipoUsuario");
        Object usuarioLogado = session.getAttribute("usuarioLogado");
        Object emailSessao = session.getAttribute("email2FA");

        if (!"admin".equals(tipoUsuario)) {
            return false;
        }

        if (!(usuarioLogado instanceof loginAdminDTO)) {
            return false;
        }

        if (!(emailSessao instanceof String)) {
            return false;
        }

        loginAdminDTO admin = (loginAdminDTO) usuarioLogado;

        if (admin.getEmail() == null || admin.getEmail().isBlank()) {
            return false;
        }

        return admin.getEmail().trim().equalsIgnoreCase(((String) emailSessao).trim());
    }
}
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
import org.slf4j.helpers.MessageFormatter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Controller
public class AdminUsuariosController {

    private static final Logger logger = LoggerFactory.getLogger(AdminUsuariosController.class);

    private static final String URL_LOGIN_ADMIN = "/admin/login";

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
            model.addAttribute("adminLogado", adminUsuarioDAO.buscarAdministradorPorEmail((String) session.getAttribute("email2FA")));

            int totalAdministradoresAtivos = 0;

            for (usuarioAdminDTO usuario : usuarios) {
                if ("Administrador".equals(usuario.getPerfil()) && usuario.isStatus()) {
                    totalAdministradoresAtivos++;
                }
            }

            model.addAttribute("totalAdministradoresAtivos", totalAdministradoresAtivos);

        } catch (SQLException e) {
            logger.error("erro ao carregar usuarios para o administrador", e);

            if (logger.isErrorEnabled()) {
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));

                    usuarioDAO.InserirLogsNoBD(null, "ERROR", AdminUsuariosController.class.getName(), "listarUsuarios", null,
                            "erro ao carregar usuarios para o administrador", null, excecaoLogBD.toString());

                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            model.addAttribute("mensagemErro", "Não foi possível carregar os usuários.");
            model.addAttribute("usuarios", new ArrayList<usuarioAdminDTO>());
            model.addAttribute("adminLogado", null);
            model.addAttribute("totalAdministradoresAtivos", 0);
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

            logger.info("Usuário cadastrado pelo administrador. perfil={} email={}", usuario.getPerfil(), usuario.getEmail());

            if (logger.isInfoEnabled()) {
                try {
                    usuarioDAO.InserirLogsNoBD(null, "INFO", AdminUsuariosController.class.getName(), "salvarUsuario", null,
                            MessageFormatter.arrayFormat("Usuário cadastrado pelo administrador. perfil={} email={}", new Object[]{usuario.getPerfil(), usuario.getEmail()}).getMessage(), null, null);

                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            redirectAttributes.addFlashAttribute("mensagemSucesso", "Usuário cadastrado com sucesso.");

            return "redirect:/admin/usuarios";

        } catch (SQLException e) {
            logger.error("erro ao cadastrar usuario pelo administrador", e);

            if (logger.isErrorEnabled()) {
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));

                    usuarioDAO.InserirLogsNoBD(null, "ERROR", AdminUsuariosController.class.getName(), "salvarUsuario", null,
                            "erro ao cadastrar usuario pelo administrador", null, excecaoLogBD.toString());

                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

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

            if ("Administrador".equals(usuario.getPerfil())) {
                redirectAttributes.addFlashAttribute("mensagemErro", "Esta página permite editar apenas alunos e professores.");
                return "redirect:/admin/usuarios";
            }

            model.addAttribute("usuario", usuario);

            return "Usuarios/Admin/editarUsuarioAdmin";

        } catch (SQLException e) {
            logger.error("erro ao buscar usuario {} para edicao", idUsuario, e);

            if (logger.isErrorEnabled()) {
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));

                    usuarioDAO.InserirLogsNoBD(null, "ERROR", AdminUsuariosController.class.getName(), "editarUsuario", null,
                            MessageFormatter.arrayFormat("erro ao buscar usuario {} para edicao", new Object[]{idUsuario}).getMessage(), null, excecaoLogBD.toString());

                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

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

            if ("Administrador".equals(usuarioAtual.getPerfil())) {
                redirectAttributes.addFlashAttribute("mensagemErro", "Esta página permite editar apenas alunos e professores.");
                return "redirect:/admin/usuarios";
            }

            // mantem o id e o perfil que ja existem no banco
            usuario.setIdUsuario(idUsuario);
            usuario.setIdPerfil(usuarioAtual.getIdPerfil());
            usuario.setPerfil(usuarioAtual.getPerfil());
            usuario.setStatus(usuarioAtual.isStatus());
            usuario.setMaster(usuarioAtual.isMaster());

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
                logger.info("Usuário atualizado pelo administrador. usuarioId={}", idUsuario);

                if (logger.isInfoEnabled()) {
                    try {
                        usuarioDAO.InserirLogsNoBD(null, "INFO", AdminUsuariosController.class.getName(), "salvarEdicaoUsuario", null,
                                MessageFormatter.arrayFormat("Usuário atualizado pelo administrador. usuarioId={}", new Object[]{idUsuario}).getMessage(), null, null);

                    } catch (Exception erroLogBD) {
                        logger.error("Erro ao gravar log no banco.", erroLogBD);
                    }
                }

                redirectAttributes.addFlashAttribute("mensagemSucesso", "Usuário atualizado com sucesso.");

            } else {
                logger.warn("Atualização de usuário não realizada. usuarioId={}", idUsuario);

                if (logger.isWarnEnabled()) {
                    try {
                        usuarioDAO.InserirLogsNoBD(null, "WARN", AdminUsuariosController.class.getName(), "salvarEdicaoUsuario", null,
                                MessageFormatter.arrayFormat("Atualização de usuário não realizada. usuarioId={}", new Object[]{idUsuario}).getMessage(), null, null);

                    } catch (Exception erroLogBD) {
                        logger.error("Erro ao gravar log no banco.", erroLogBD);
                    }
                }

                redirectAttributes.addFlashAttribute("mensagemErro", "Não foi possível atualizar o usuário.");
            }

            return "redirect:/admin/usuarios";

        } catch (SQLException e) {
            logger.error("erro ao atualizar usuario {}", idUsuario, e);

            if (logger.isErrorEnabled()) {
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));

                    usuarioDAO.InserirLogsNoBD(null, "ERROR", AdminUsuariosController.class.getName(), "salvarEdicaoUsuario", null,
                            MessageFormatter.arrayFormat("erro ao atualizar usuario {}", new Object[]{idUsuario}).getMessage(), null, excecaoLogBD.toString());

                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

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
            boolean alterado = adminUsuarioDAO.atualizarStatusUsuario(idUsuario, false, (String) session.getAttribute("email2FA"));

            if (alterado) {
                logger.info("Usuário desativado pelo administrador. usuarioId={}", idUsuario);

                if (logger.isInfoEnabled()) {
                    try {
                        usuarioDAO.InserirLogsNoBD(null, "INFO", AdminUsuariosController.class.getName(), "desativarUsuario", null,
                                MessageFormatter.arrayFormat("Usuário desativado pelo administrador. usuarioId={}", new Object[]{idUsuario}).getMessage(), null, null);

                    } catch (Exception erroLogBD) {
                        logger.error("Erro ao gravar log no banco.", erroLogBD);
                    }
                }

                redirectAttributes.addFlashAttribute("mensagemSucesso", "Usuário desativado com sucesso.");

            } else {
                logger.warn("Desativação de usuário não realizada. usuarioId={}", idUsuario);

                if (logger.isWarnEnabled()) {
                    try {
                        usuarioDAO.InserirLogsNoBD(null, "WARN", AdminUsuariosController.class.getName(), "desativarUsuario", null,
                                MessageFormatter.arrayFormat("Desativação de usuário não realizada. usuarioId={}", new Object[]{idUsuario}).getMessage(), null, null);

                    } catch (Exception erroLogBD) {
                        logger.error("Erro ao gravar log no banco.", erroLogBD);
                    }
                }

                redirectAttributes.addFlashAttribute("mensagemErro", "Não foi possível desativar o usuário.");
            }

        } catch (IllegalStateException e) {
            logger.warn("Ação bloqueada. usuarioId={} motivo={}", idUsuario, e.getMessage());
            registrarBloqueio("desativarUsuario", idUsuario, e.getMessage());
            redirectAttributes.addFlashAttribute("mensagemErro", e.getMessage());

        } catch (SQLException e) {
            logger.error("erro ao desativar usuario {}", idUsuario, e);

            if (logger.isErrorEnabled()) {
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));

                    usuarioDAO.InserirLogsNoBD(null, "ERROR", AdminUsuariosController.class.getName(), "desativarUsuario", null,
                            "erro ao desativar usuario {}", null, excecaoLogBD.toString());

                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

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
            boolean alterado = adminUsuarioDAO.atualizarStatusUsuario(idUsuario, true, (String) session.getAttribute("email2FA"));

            if (alterado) {
                logger.info("Usuário ativado pelo administrador. usuarioId={}", idUsuario);

                if (logger.isInfoEnabled()) {
                    try {
                        usuarioDAO.InserirLogsNoBD(null, "INFO", AdminUsuariosController.class.getName(), "ativarUsuario", null,
                                MessageFormatter.arrayFormat("Usuário ativado pelo administrador. usuarioId={}", new Object[]{idUsuario}).getMessage(), null, null);

                    } catch (Exception erroLogBD) {
                        logger.error("Erro ao gravar log no banco.", erroLogBD);
                    }
                }

                redirectAttributes.addFlashAttribute("mensagemSucesso", "Usuário ativado com sucesso.");

            } else {
                logger.warn("Ativação de usuário não realizada. usuarioId={}", idUsuario);

                if (logger.isWarnEnabled()) {
                    try {
                        usuarioDAO.InserirLogsNoBD(null, "WARN", AdminUsuariosController.class.getName(), "ativarUsuario", null,
                                MessageFormatter.arrayFormat("Ativação de usuário não realizada. usuarioId={}", new Object[]{idUsuario}).getMessage(), null, null);

                    } catch (Exception erroLogBD) {
                        logger.error("Erro ao gravar log no banco.", erroLogBD);
                    }
                }

                redirectAttributes.addFlashAttribute("mensagemErro", "Não foi possível ativar o usuário.");
            }

        } catch (IllegalStateException e) {
            logger.warn("Ação bloqueada. usuarioId={} motivo={}", idUsuario, e.getMessage());
            registrarBloqueio("ativarUsuario", idUsuario, e.getMessage());
            redirectAttributes.addFlashAttribute("mensagemErro", e.getMessage());

        } catch (SQLException e) {
            logger.error("erro ao ativar usuario {}", idUsuario, e);

            if (logger.isErrorEnabled()) {
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));

                    usuarioDAO.InserirLogsNoBD(null, "ERROR", AdminUsuariosController.class.getName(), "ativarUsuario", null,
                            MessageFormatter.arrayFormat("erro ao ativar usuario {}", new Object[]{idUsuario}).getMessage(), null, excecaoLogBD.toString());

                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            redirectAttributes.addFlashAttribute("mensagemErro", "Erro ao ativar o usuário.");
        }

        return "redirect:/admin/usuarios";
    }

    @PostMapping("/admin/usuarios/{idUsuario}/excluir")
    public String excluirAdministrador(@PathVariable Long idUsuario, HttpSession session, RedirectAttributes redirectAttributes) {

        if (session.getAttribute("usuarioLogado") == null) {
            return "redirect:" + URL_LOGIN_ADMIN;
        }

        if (!ehAdministrador(session)) {
            return "redirect:/home";
        }

        try {
            String emailAdmin = (String) session.getAttribute("email2FA");
            boolean excluido = adminUsuarioDAO.excluirAdministrador(idUsuario, emailAdmin);

            if (excluido) {
                logger.info("Administrador excluído. usuarioId={} responsavel={}", idUsuario, emailAdmin);

                if (logger.isInfoEnabled()) {
                    try {
                        usuarioDAO.InserirLogsNoBD(null, "INFO", AdminUsuariosController.class.getName(), "excluirAdministrador", null,
                                MessageFormatter.arrayFormat("Administrador excluído. usuarioId={} responsavel={}", new Object[]{idUsuario, emailAdmin}).getMessage(), null, null);

                    } catch (Exception erroLogBD) {
                        logger.error("Erro ao gravar log no banco.", erroLogBD);
                    }
                }

                redirectAttributes.addFlashAttribute("mensagemSucesso", "Administrador excluído com sucesso.");

            } else {
                redirectAttributes.addFlashAttribute("mensagemErro", "Administrador não encontrado.");
            }

        } catch (IllegalStateException e) {
            logger.warn("Exclusão bloqueada. usuarioId={} motivo={}", idUsuario, e.getMessage());
            registrarBloqueio("excluirAdministrador", idUsuario, e.getMessage());
            redirectAttributes.addFlashAttribute("mensagemErro", e.getMessage());

        } catch (SQLException e) {
            logger.error("Erro ao excluir administrador. usuarioId={}", idUsuario, e);

            if (logger.isErrorEnabled()) {
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));

                    usuarioDAO.InserirLogsNoBD(null, "ERROR", AdminUsuariosController.class.getName(), "excluirAdministrador", null,
                            MessageFormatter.arrayFormat("Erro ao excluir administrador. usuarioId={}", new Object[]{idUsuario}).getMessage(), null, excecaoLogBD.toString());

                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            redirectAttributes.addFlashAttribute("mensagemErro", "Não foi possível excluir o administrador. A conta pode estar vinculada a outros registros.");
        }

        return "redirect:/admin/usuarios";
    }

    private void registrarBloqueio(String metodo, Long idUsuario, String motivo) {
        if (logger.isWarnEnabled()) {
            try {
                usuarioDAO.InserirLogsNoBD(null, "WARN", AdminUsuariosController.class.getName(), metodo, null,
                        MessageFormatter.arrayFormat("Ação bloqueada. usuarioId={} motivo={}", new Object[]{idUsuario, motivo}).getMessage(), null, null);

            } catch (Exception erroLogBD) {
                logger.error("Erro ao gravar log no banco.", erroLogBD);
            }
        }
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

        if (!admin.getEmail().trim().equalsIgnoreCase(((String) emailSessao).trim())) {
            return false;
        }

        try {
            usuarioAdminDTO adminAtual = adminUsuarioDAO.buscarAdministradorPorEmail(admin.getEmail());
            return adminAtual != null && adminAtual.isStatus();

        } catch (SQLException e) {
            logger.error("Erro ao verificar a conta do administrador.", e);

            if (logger.isErrorEnabled()) {
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));

                    usuarioDAO.InserirLogsNoBD(null, "ERROR", AdminUsuariosController.class.getName(), "ehAdministrador", null,
                            "Erro ao verificar a conta do administrador.", null, excecaoLogBD.toString());

                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            return false;
        }
    }
}
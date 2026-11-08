// contas administrativas disponibiliza consulta cadastro edicao e mudancas de situacao
package com.example.CampusLink.controller.Usuarios.Admin;

import com.example.CampusLink.repository.AdminUsuarioRepository;
import com.example.CampusLink.repository.UsuarioRepository;
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

// controle de usuarios esta classe valida os formularios e verifica as permissoes do administrador
@Controller
public class AdminUsuariosController {

    private static final Logger logger = LoggerFactory.getLogger(AdminUsuariosController.class);

    private static final String URL_LOGIN_ADMIN = "/admin/login";

    private final AdminUsuarioRepository adminUsuarioRepository;
    private final UsuarioRepository UsuarioRepository;

    // preparar controle administrativo de usuarios este construtor recebe as consultas e alteracoes de contas
    public AdminUsuariosController(AdminUsuarioRepository adminUsuarioRepository, UsuarioRepository UsuarioRepository) {
        this.adminUsuarioRepository = adminUsuarioRepository;
        this.UsuarioRepository = UsuarioRepository;
    }

    // listar usuarios administrativos este metodo carrega as contas e conta os administradores ativos
    @GetMapping("/admin/usuarios")
    public String listarUsuarios(HttpSession session, Model model) {


        // exigir login administrativo encaminha sessoes sem usuario para o acesso do administrador
        if (session.getAttribute("usuarioLogado") == null) {
            return "redirect:" + URL_LOGIN_ADMIN;
        }


        // exigir administrador retorna ao inicio quando a sessao nao comprova o acesso administrativo
        if (!ehAdministrador(session)) {
            return "redirect:/home";
        }

        // carregar usuarios administrativos consulta contas e administradores ativos e trata falhas do banco
        try {
            List<usuarioAdminDTO> usuarios = adminUsuarioRepository.listarUsuarios();
            model.addAttribute("usuarios", usuarios);
            model.addAttribute("adminLogado", adminUsuarioRepository.buscarAdministradorPorEmail((String) session.getAttribute("email2FA")));

            int totalAdministradoresAtivos = 0;

            for (usuarioAdminDTO usuario : usuarios) {
                // contar administradores ativos soma apenas contas administrativas que estao habilitadas
                if ("Administrador".equals(usuario.getPerfil()) && usuario.isStatus()) {
                    totalAdministradoresAtivos++;
                }
            }

            model.addAttribute("totalAdministradoresAtivos", totalAdministradoresAtivos);

        } catch (SQLException e) {
            logger.error("erro ao carregar usuarios para o administrador", e);

            // registro de erros confere se esta habilitado ao listar usuarios administrativos
            if (logger.isErrorEnabled()) {
                // gravar historico ao listar usuarios administrativos tenta salvar o registro no banco e informa falhas
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));

                    UsuarioRepository.InserirLogsNoBD(null, "ERROR", AdminUsuariosController.class.getName(), "listarUsuarios", null,
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

    // abrir cadastro administrativo de usuario este metodo prepara os campos para uma nova conta
    @GetMapping("/admin/usuarios/cadastrar")
    public String cadastrarUsuario(HttpSession session, Model model) {

        // exigir login administrativo encaminha sessoes sem usuario para o acesso do administrador
        if (session.getAttribute("usuarioLogado") == null) {
            return "redirect:" + URL_LOGIN_ADMIN;
        }

        // exigir administrador retorna ao inicio quando a sessao nao comprova o acesso administrativo
        if (!ehAdministrador(session)) {
            return "redirect:/home";
        }

        model.addAttribute("usuario", new cadastrarUsuarioAdminDTO());

        return "Usuarios/Admin/cadastrarUsuarioAdmin";
    }

    // cadastrar usuario pela administracao este metodo valida dados repetidos e grava a senha protegida
    @PostMapping("/admin/usuarios/cadastrar")
    public String salvarUsuario(
            @Valid @ModelAttribute("usuario") cadastrarUsuarioAdminDTO usuario,
            BindingResult result,
            HttpSession session,
            Model model,
            RedirectAttributes redirectAttributes
    ) {

        // exigir login administrativo encaminha sessoes sem usuario para o acesso do administrador
        if (session.getAttribute("usuarioLogado") == null) {
            return "redirect:" + URL_LOGIN_ADMIN;
        }

        // exigir administrador retorna ao inicio quando a sessao nao comprova o acesso administrativo
        if (!ehAdministrador(session)) {
            return "redirect:/home";
        }

        // validar cadastro administrativo reapresenta o formulario quando os campos possuem erros
        if (result.hasErrors()) {
            return "Usuarios/Admin/cadastrarUsuarioAdmin";
        }

        // conferir senha do novo usuario recusa senhas diferentes da confirmacao
        if (!usuario.getSenha().equals(usuario.getConfirmarSenha())) {
            model.addAttribute("mensagemErro", "As senhas não são iguais.");
            return "Usuarios/Admin/cadastrarUsuarioAdmin";
        }

        // interpretar nascimento do cadastro recusa uma data que nao pode ser convertida
        try {
            LocalDate.parse(usuario.getDataNasc());

        } catch (DateTimeParseException e) {
            model.addAttribute("mensagemErro", "Informe uma data de nascimento válida.");
            return "Usuarios/Admin/cadastrarUsuarioAdmin";
        }

        // cadastrar conta administrativa verifica duplicidades protege a senha e trata falhas de gravacao
        try {
            List<String> erros = UsuarioRepository.validarDadosDuplicados(
                    usuario.getPerfil(),
                    usuario.getIdentificador(),
                    usuario.getEmail(),
                    usuario.getTelefone()
            );

            // impedir dados duplicados reapresenta o cadastro com os conflitos encontrados
            if (!erros.isEmpty()) {
                model.addAttribute("mensagemErro", String.join(". ", erros) + ".");
                return "Usuarios/Admin/cadastrarUsuarioAdmin";
            }

            BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);
            String senhaCriptografada = encoder.encode(usuario.getSenha());

            UsuarioRepository.InsertCadastroUsuarioIntoBD(
                    usuario.getPerfil(),
                    usuario.getIdentificador(),
                    usuario.getNome(),
                    usuario.getEmail(),
                    usuario.getTelefone(),
                    usuario.getDataNasc(),
                    senhaCriptografada
            );

            logger.info("Usuário cadastrado pelo administrador. perfil={} email={}", usuario.getPerfil(), usuario.getEmail());

            // registro de informacoes confere se esta habilitado ao cadastrar usuario pela administracao
            if (logger.isInfoEnabled()) {
                // gravar historico ao cadastrar usuario pela administracao tenta salvar o registro no banco e informa falhas
                try {
                    UsuarioRepository.InserirLogsNoBD(null, "INFO", AdminUsuariosController.class.getName(), "salvarUsuario", null,
                            MessageFormatter.arrayFormat("Usuário cadastrado pelo administrador. perfil={} email={}", new Object[]{usuario.getPerfil(), usuario.getEmail()}).getMessage(), null, null);

                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            redirectAttributes.addFlashAttribute("mensagemSucesso", "Usuário cadastrado com sucesso.");

            return "redirect:/admin/usuarios";

        } catch (SQLException e) {
            logger.error("erro ao cadastrar usuario pelo administrador", e);

            // registro de erros confere se esta habilitado ao cadastrar usuario pela administracao
            if (logger.isErrorEnabled()) {
                // gravar historico ao cadastrar usuario pela administracao tenta salvar o registro no banco e informa falhas
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));

                    UsuarioRepository.InserirLogsNoBD(null, "ERROR", AdminUsuariosController.class.getName(), "salvarUsuario", null,
                            "erro ao cadastrar usuario pelo administrador", null, excecaoLogBD.toString());

                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            model.addAttribute("mensagemErro", "Não foi possível cadastrar o usuário.");

            return "Usuarios/Admin/cadastrarUsuarioAdmin";
        }
    }

    // abrir edicao administrativa de usuario este metodo carrega apenas contas de alunos e professores
    @GetMapping("/admin/usuarios/{idUsuario}/editar")
    public String editarUsuario(
            @PathVariable Long idUsuario,
            HttpSession session,
            Model model,
            RedirectAttributes redirectAttributes
    ) {

        // exigir login administrativo encaminha sessoes sem usuario para o acesso do administrador
        if (session.getAttribute("usuarioLogado") == null) {
            return "redirect:" + URL_LOGIN_ADMIN;
        }

        // exigir administrador retorna ao inicio quando a sessao nao comprova o acesso administrativo
        if (!ehAdministrador(session)) {
            return "redirect:/home";
        }

        // carregar usuario para editar consulta a conta e informa falhas do banco
        try {
            usuarioAdminDTO usuario = adminUsuarioRepository.buscarUsuarioPorId(idUsuario);

            // informar usuario ausente retorna a lista quando a conta nao foi encontrada
            if (usuario == null) {
                redirectAttributes.addFlashAttribute("mensagemErro", "Usuário não encontrado.");
                return "redirect:/admin/usuarios";
            }

            // proteger edicao de administrador limita essa pagina aos dados de alunos e professores
            if ("Administrador".equals(usuario.getPerfil())) {
                redirectAttributes.addFlashAttribute("mensagemErro", "Esta página permite editar apenas alunos e professores.");
                return "redirect:/admin/usuarios";
            }

            model.addAttribute("usuario", usuario);

            return "Usuarios/Admin/editarUsuarioAdmin";

        } catch (SQLException e) {
            logger.error("erro ao buscar usuario {} para edicao", idUsuario, e);

            // registro de erros confere se esta habilitado ao abrir edicao administrativa de usuario
            if (logger.isErrorEnabled()) {
                // gravar historico ao abrir edicao administrativa de usuario tenta salvar o registro no banco e informa falhas
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));

                    UsuarioRepository.InserirLogsNoBD(null, "ERROR", AdminUsuariosController.class.getName(), "editarUsuario", null,
                            MessageFormatter.arrayFormat("erro ao buscar usuario {} para edicao", new Object[]{idUsuario}).getMessage(), null, excecaoLogBD.toString());

                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            redirectAttributes.addFlashAttribute("mensagemErro", "Não foi possível carregar o usuário.");

            return "redirect:/admin/usuarios";
        }
    }

    // editar usuario pela administracao este metodo preserva o perfil e valida os dados antes de atualizar
    @PostMapping("/admin/usuarios/{idUsuario}/editar")
    public String salvarEdicaoUsuario(
            @PathVariable Long idUsuario,
            @Valid @ModelAttribute("usuario") usuarioAdminDTO usuario,
            BindingResult bindingResult,
            HttpSession session,
            Model model,
            RedirectAttributes redirectAttributes
    ) {

        // exigir login administrativo encaminha sessoes sem usuario para o acesso do administrador
        if (session.getAttribute("usuarioLogado") == null) {
            return "redirect:" + URL_LOGIN_ADMIN;
        }

        // exigir administrador retorna ao inicio quando a sessao nao comprova o acesso administrativo
        if (!ehAdministrador(session)) {
            return "redirect:/home";
        }

        // salvar dados do usuario confere a conta valida campos e duplicidades e trata falhas de atualizacao
        try {
            usuarioAdminDTO usuarioAtual = adminUsuarioRepository.buscarUsuarioPorId(idUsuario);

            // exigir conta existente interrompe a edicao quando o usuario nao foi encontrado
            if (usuarioAtual == null) {
                redirectAttributes.addFlashAttribute("mensagemErro", "Usuário não encontrado.");
                return "redirect:/admin/usuarios";
            }

            // restringir alteracao administrativa impede usar esse formulario para editar administradores
            if ("Administrador".equals(usuarioAtual.getPerfil())) {
                redirectAttributes.addFlashAttribute("mensagemErro", "Esta página permite editar apenas alunos e professores.");
                return "redirect:/admin/usuarios";
            }


            usuario.setIdUsuario(idUsuario);
            usuario.setIdPerfil(usuarioAtual.getIdPerfil());
            usuario.setPerfil(usuarioAtual.getPerfil());
            usuario.setStatus(usuarioAtual.isStatus());
            usuario.setMaster(usuarioAtual.isMaster());

            // corrigir dados administrativos reapresenta o formulario com a primeira regra violada
            if (bindingResult.hasErrors()) {
                model.addAttribute("mensagemErro", bindingResult.getFieldError().getDefaultMessage());
                model.addAttribute("usuario", usuario);

                return "Usuarios/Admin/editarUsuarioAdmin";
            }

            String erroData = validarDataNascimento(usuario);
            // corrigir data administrativa rejeita datas inexistentes antes de atualizar a conta
            if (erroData != null) {
                model.addAttribute("mensagemErro", erroData);
                model.addAttribute("usuario", usuario);

                return "Usuarios/Admin/editarUsuarioAdmin";
            }

            List<String> erros = adminUsuarioRepository.validarDadosEdicao(
                    idUsuario,
                    usuario.getPerfil(),
                    usuario.getIdentificador(),
                    usuario.getEmail(),
                    usuario.getTelefone()
            );

            // corrigir duplicidades na edicao reapresenta os dados com os conflitos encontrados
            if (!erros.isEmpty()) {
                model.addAttribute("mensagemErro", String.join(" ", erros));
                model.addAttribute("usuario", usuario);

                return "Usuarios/Admin/editarUsuarioAdmin";
            }

            boolean atualizado = adminUsuarioRepository.atualizarUsuario(usuario);

            // informar resultado da edicao distingue usuario atualizado de gravacao nao realizada
            if (atualizado) {
                logger.info("Usuário atualizado pelo administrador. usuarioId={}", idUsuario);

                // registro de informacoes confere se esta habilitado ao editar usuario pela administracao
                if (logger.isInfoEnabled()) {
                    // gravar historico ao editar usuario pela administracao tenta salvar o registro no banco e informa falhas
                    try {
                        UsuarioRepository.InserirLogsNoBD(null, "INFO", AdminUsuariosController.class.getName(), "salvarEdicaoUsuario", null,
                                MessageFormatter.arrayFormat("Usuário atualizado pelo administrador. usuarioId={}", new Object[]{idUsuario}).getMessage(), null, null);

                    } catch (Exception erroLogBD) {
                        logger.error("Erro ao gravar log no banco.", erroLogBD);
                    }
                }

                redirectAttributes.addFlashAttribute("mensagemSucesso", "Usuário atualizado com sucesso.");

            } else {
                logger.warn("Atualização de usuário não realizada. usuarioId={}", idUsuario);

                // registro de avisos confere se esta habilitado ao editar usuario pela administracao
                if (logger.isWarnEnabled()) {
                    // gravar historico ao editar usuario pela administracao tenta salvar o registro no banco e informa falhas
                    try {
                        UsuarioRepository.InserirLogsNoBD(null, "WARN", AdminUsuariosController.class.getName(), "salvarEdicaoUsuario", null,
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

            // registro de erros confere se esta habilitado ao editar usuario pela administracao
            if (logger.isErrorEnabled()) {
                // gravar historico ao editar usuario pela administracao tenta salvar o registro no banco e informa falhas
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));

                    UsuarioRepository.InserirLogsNoBD(null, "ERROR", AdminUsuariosController.class.getName(), "salvarEdicaoUsuario", null,
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

    // desativar usuario pela administracao este metodo solicita a desativacao e apresenta eventuais impedimentos
    @PostMapping("/admin/usuarios/{idUsuario}/desativar")
    public String desativarUsuario(
            @PathVariable Long idUsuario,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {

        // exigir login administrativo encaminha sessoes sem usuario para o acesso do administrador
        if (session.getAttribute("usuarioLogado") == null) {
            return "redirect:" + URL_LOGIN_ADMIN;
        }

        // exigir administrador retorna ao inicio quando a sessao nao comprova o acesso administrativo
        if (!ehAdministrador(session)) {
            return "redirect:/home";
        }

        // desativar conta tenta alterar a situacao e informa impedimentos ou falhas do banco
        try {
            boolean alterado = adminUsuarioRepository.atualizarStatusUsuario(idUsuario, false, (String) session.getAttribute("email2FA"));

            // confirmar desativacao mostra sucesso apenas quando a conta foi alterada
            if (alterado) {
                logger.info("Usuário desativado pelo administrador. usuarioId={}", idUsuario);

                // registro de informacoes confere se esta habilitado ao desativar usuario pela administracao
                if (logger.isInfoEnabled()) {
                    // gravar historico ao desativar usuario pela administracao tenta salvar o registro no banco e informa falhas
                    try {
                        UsuarioRepository.InserirLogsNoBD(null, "INFO", AdminUsuariosController.class.getName(), "desativarUsuario", null,
                                MessageFormatter.arrayFormat("Usuário desativado pelo administrador. usuarioId={}", new Object[]{idUsuario}).getMessage(), null, null);

                    } catch (Exception erroLogBD) {
                        logger.error("Erro ao gravar log no banco.", erroLogBD);
                    }
                }

                redirectAttributes.addFlashAttribute("mensagemSucesso", "Usuário desativado com sucesso.");

            } else {
                logger.warn("Desativação de usuário não realizada. usuarioId={}", idUsuario);

                // registro de avisos confere se esta habilitado ao desativar usuario pela administracao
                if (logger.isWarnEnabled()) {
                    // gravar historico ao desativar usuario pela administracao tenta salvar o registro no banco e informa falhas
                    try {
                        UsuarioRepository.InserirLogsNoBD(null, "WARN", AdminUsuariosController.class.getName(), "desativarUsuario", null,
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

            // registro de erros confere se esta habilitado ao desativar usuario pela administracao
            if (logger.isErrorEnabled()) {
                // gravar historico ao desativar usuario pela administracao tenta salvar o registro no banco e informa falhas
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));

                    UsuarioRepository.InserirLogsNoBD(null, "ERROR", AdminUsuariosController.class.getName(), "desativarUsuario", null,
                            "erro ao desativar usuario {}", null, excecaoLogBD.toString());

                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            redirectAttributes.addFlashAttribute("mensagemErro", "Erro ao desativar o usuário.");
        }

        return "redirect:/admin/usuarios";
    }

    // ativar usuario pela administracao este metodo solicita a ativacao e apresenta eventuais impedimentos
    @PostMapping("/admin/usuarios/{idUsuario}/ativar")
    public String ativarUsuario(
            @PathVariable Long idUsuario,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {

        // exigir login administrativo encaminha sessoes sem usuario para o acesso do administrador
        if (session.getAttribute("usuarioLogado") == null) {
            return "redirect:" + URL_LOGIN_ADMIN;
        }

        // exigir administrador retorna ao inicio quando a sessao nao comprova o acesso administrativo
        if (!ehAdministrador(session)) {
            return "redirect:/home";
        }

        // ativar conta tenta alterar a situacao e informa impedimentos ou falhas do banco
        try {
            boolean alterado = adminUsuarioRepository.atualizarStatusUsuario(idUsuario, true, (String) session.getAttribute("email2FA"));

            // confirmar ativacao mostra sucesso apenas quando a conta foi alterada
            if (alterado) {
                logger.info("Usuário ativado pelo administrador. usuarioId={}", idUsuario);

                // registro de informacoes confere se esta habilitado ao ativar usuario pela administracao
                if (logger.isInfoEnabled()) {
                    // gravar historico ao ativar usuario pela administracao tenta salvar o registro no banco e informa falhas
                    try {
                        UsuarioRepository.InserirLogsNoBD(null, "INFO", AdminUsuariosController.class.getName(), "ativarUsuario", null,
                                MessageFormatter.arrayFormat("Usuário ativado pelo administrador. usuarioId={}", new Object[]{idUsuario}).getMessage(), null, null);

                    } catch (Exception erroLogBD) {
                        logger.error("Erro ao gravar log no banco.", erroLogBD);
                    }
                }

                redirectAttributes.addFlashAttribute("mensagemSucesso", "Usuário ativado com sucesso.");

            } else {
                logger.warn("Ativação de usuário não realizada. usuarioId={}", idUsuario);

                // registro de avisos confere se esta habilitado ao ativar usuario pela administracao
                if (logger.isWarnEnabled()) {
                    // gravar historico ao ativar usuario pela administracao tenta salvar o registro no banco e informa falhas
                    try {
                        UsuarioRepository.InserirLogsNoBD(null, "WARN", AdminUsuariosController.class.getName(), "ativarUsuario", null,
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

            // registro de erros confere se esta habilitado ao ativar usuario pela administracao
            if (logger.isErrorEnabled()) {
                // gravar historico ao ativar usuario pela administracao tenta salvar o registro no banco e informa falhas
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));

                    UsuarioRepository.InserirLogsNoBD(null, "ERROR", AdminUsuariosController.class.getName(), "ativarUsuario", null,
                            MessageFormatter.arrayFormat("erro ao ativar usuario {}", new Object[]{idUsuario}).getMessage(), null, excecaoLogBD.toString());

                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            redirectAttributes.addFlashAttribute("mensagemErro", "Erro ao ativar o usuário.");
        }

        return "redirect:/admin/usuarios";
    }

    // delete administrador pela tela este metodo solicita a exclusao e mostra os impedimentos da conta
    @PostMapping("/admin/usuarios/{idUsuario}/excluir")
    public String excluirAdministrador(@PathVariable Long idUsuario, HttpSession session, RedirectAttributes redirectAttributes) {

        // exigir login administrativo encaminha sessoes sem usuario para o acesso do administrador
        if (session.getAttribute("usuarioLogado") == null) {
            return "redirect:" + URL_LOGIN_ADMIN;
        }

        // exigir administrador retorna ao inicio quando a sessao nao comprova o acesso administrativo
        if (!ehAdministrador(session)) {
            return "redirect:/home";
        }

        // delete administrador tenta remover a conta e informa restricoes ou vinculos que impedem a exclusao
        try {
            String emailAdmin = (String) session.getAttribute("email2FA");
            boolean excluido = adminUsuarioRepository.excluirAdministrador(idUsuario, emailAdmin);

            // confirmar delete administrador distingue conta excluida de administrador nao encontrado
            if (excluido) {
                logger.info("Administrador excluído. usuarioId={} responsavel={}", idUsuario, emailAdmin);

                // registro de informacoes confere se esta habilitado ao delete administrador pela tela
                if (logger.isInfoEnabled()) {
                    // gravar historico ao delete administrador pela tela tenta salvar o registro no banco e informa falhas
                    try {
                        UsuarioRepository.InserirLogsNoBD(null, "INFO", AdminUsuariosController.class.getName(), "excluirAdministrador", null,
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

            // registro de erros confere se esta habilitado ao delete administrador pela tela
            if (logger.isErrorEnabled()) {
                // gravar historico ao delete administrador pela tela tenta salvar o registro no banco e informa falhas
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));

                    UsuarioRepository.InserirLogsNoBD(null, "ERROR", AdminUsuariosController.class.getName(), "excluirAdministrador", null,
                            MessageFormatter.arrayFormat("Erro ao excluir administrador. usuarioId={}", new Object[]{idUsuario}).getMessage(), null, excecaoLogBD.toString());

                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            redirectAttributes.addFlashAttribute("mensagemErro", "Não foi possível excluir o administrador. A conta pode estar vinculada a outros registros.");
        }

        return "redirect:/admin/usuarios";
    }

    // registrar bloqueio administrativo este metodo grava no historico a acao recusada e seu motivo
    private void registrarBloqueio(String metodo, Long idUsuario, String motivo) {
        // registro de avisos confere se esta habilitado ao registrar bloqueio administrativo
        if (logger.isWarnEnabled()) {
            // gravar historico ao registrar bloqueio administrativo tenta salvar o registro no banco e informa falhas
            try {
                UsuarioRepository.InserirLogsNoBD(null, "WARN", AdminUsuariosController.class.getName(), metodo, null,
                        MessageFormatter.arrayFormat("Ação bloqueada. usuarioId={} motivo={}", new Object[]{idUsuario, motivo}).getMessage(), null, null);

            } catch (Exception erroLogBD) {
                logger.error("Erro ao gravar log no banco.", erroLogBD);
            }
        }
    }

    // validar data de nascimento interpreta a data informada e recusa dias inexistentes
    private String validarDataNascimento(usuarioAdminDTO usuario) {
        // validar formato do nascimento tenta interpretar a data e informa um valor invalido
        try {
            LocalDate.parse(usuario.getDataNasc());

        } catch (DateTimeParseException e) {
            return "Informe uma data de nascimento válida.";
        }

        return null;
    }

    // validar administrador dos usuarios este metodo confere a sessao e exige uma conta ainda ativa
    private boolean ehAdministrador(HttpSession session) {

        Object tipoUsuario = session.getAttribute("tipoUsuario");
        Object usuarioLogado = session.getAttribute("usuarioLogado");
        Object emailSessao = session.getAttribute("email2FA");

        // conferir perfil administrativo rejeita sessoes com outro tipo de usuario
        if (!"admin".equals(tipoUsuario)) {
            return false;
        }

        // conferir dados administrativos rejeita um usuario que nao possui os dados de administrador
        if (!(usuarioLogado instanceof loginAdminDTO)) {
            return false;
        }

        // conferir email da sessao rejeita valores que nao sao texto
        if (!(emailSessao instanceof String)) {
            return false;
        }

        loginAdminDTO admin = (loginAdminDTO) usuarioLogado;

        // exigir email do administrador rejeita contas sem email preenchido
        if (admin.getEmail() == null || admin.getEmail().isBlank()) {
            return false;
        }

        // comparar email administrativo rejeita sessoes cujo email difere da conta autenticada
        if (!admin.getEmail().trim().equalsIgnoreCase(((String) emailSessao).trim())) {
            return false;
        }

        // conferir administrador ativo consulta a conta atual e recusa o acesso se o banco falhar
        try {
            usuarioAdminDTO adminAtual = adminUsuarioRepository.buscarAdministradorPorEmail(admin.getEmail());
            return adminAtual != null && adminAtual.isStatus();

        } catch (SQLException e) {
            logger.error("Erro ao verificar a conta do administrador.", e);

            // registro de erros confere se esta habilitado ao validar administrador dos usuarios
            if (logger.isErrorEnabled()) {
                // gravar historico ao validar administrador dos usuarios tenta salvar o registro no banco e informa falhas
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));

                    UsuarioRepository.InserirLogsNoBD(null, "ERROR", AdminUsuariosController.class.getName(), "ehAdministrador", null,
                            "Erro ao verificar a conta do administrador.", null, excecaoLogBD.toString());

                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            return false;
        }
    }
}